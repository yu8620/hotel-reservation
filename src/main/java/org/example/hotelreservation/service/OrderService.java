package org.example.hotelreservation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.common.BizException;
import org.example.hotelreservation.common.ResultCode;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.dto.CreateOrderRequest;
import org.example.hotelreservation.dto.OrderResponse;
import org.example.hotelreservation.entity.BookingOrder;
import org.example.hotelreservation.entity.Hotel;
import org.example.hotelreservation.entity.OrderNight;
import org.example.hotelreservation.entity.RoomInventory;
import org.example.hotelreservation.entity.RoomType;
import org.example.hotelreservation.enums.OrderStatus;
import org.example.hotelreservation.cache.HotelReadCache;
import org.example.hotelreservation.inventory.InventoryService;
import org.example.hotelreservation.mapper.BookingOrderMapper;
import org.example.hotelreservation.mapper.HotelMapper;
import org.example.hotelreservation.mapper.OrderNightMapper;
import org.example.hotelreservation.mapper.RoomTypeMapper;
import org.example.hotelreservation.mq.OrderTimeoutPublisher;
import org.example.hotelreservation.util.StayDates;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
/**
 * 订单应用服务：下单预占库存、支付/取消状态机、超时关单回补。
 * <p>库存扣减委托 {@link org.example.hotelreservation.inventory.InventoryService}；
 * 读缓存失效委托 {@link org.example.hotelreservation.cache.HotelReadCache}；
 * 延迟关单委托 MQ 组件。
 */
public class OrderService {

    private final BookingOrderMapper orderMapper;
    private final OrderNightMapper orderNightMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final HotelMapper hotelMapper;
    private final InventoryService inventoryService;
    private final OrderTimeoutPublisher timeoutPublisher;
    private final HotelProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final HotelReadCache hotelReadCache;

    /**
     * 创建订单（面试主路径）。
     * 顺序：requestId 幂等 → Lua 预扣 → 事务内 MySQL 扣减+落单 → 发延迟关单 → 失效读缓存。
     * 任一失败要回补 Redis，避免「Redis 少了、订单没有」。
     */
    public OrderResponse create(Long userId, CreateOrderRequest request) {
        // ===== 1. 发送幂等：相同 requestId 直接返回已有订单 =====
        BookingOrder existed = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getRequestId, request.getRequestId()));
        if (existed != null) {
            return toResponse(existed);
        }

        // ===== 2. 展开入住区间为多晚日历，并校验房型/库存行齐全 =====
        List<LocalDate> nights = StayDates.nights(request.getCheckIn(), request.getCheckOut());
        RoomType roomType = roomTypeMapper.selectById(request.getRoomTypeId());
        if (roomType == null) {
            throw new BizException(ResultCode.NOT_FOUND, "房型不存在");
        }
        int rooms = request.getRooms();
        List<RoomInventory> rows = inventoryService.loadRows(roomType.getId(), nights);
        if (rows.size() != nights.size()) {
            throw new BizException(ResultCode.SOLD_OUT, "所选日期未开放预订");
        }

        // ===== 3. Redis Lua 预扣：跨晚全有或全无；Redis 挂则失败关闭（不降级 MySQL） =====
        boolean redisOk = inventoryService.tryDeductRedis(roomType.getId(), nights, rooms);
        if (!redisOk) {
            throw new BizException(ResultCode.SOLD_OUT);
        }

        // ===== 4. 事务落单：MySQL 条件扣减 + 写订单；失败必须 restoreRedis =====
        BookingOrder order;
        try {
            order = transactionTemplate.execute(status -> persistOrder(userId, request, roomType, nights, rows));
        } catch (DuplicateKeyException duplicate) {
            // 并发下唯一键冲突：回补 Redis，再按 requestId 读已有单
            inventoryService.restoreRedis(roomType.getId(), nights, rooms);
            BookingOrder again = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                    .eq(BookingOrder::getRequestId, request.getRequestId()));
            if (again != null) {
                return toResponse(again);
            }
            throw new BizException(ResultCode.DUPLICATE_REQUEST);
        } catch (RuntimeException ex) {
            inventoryService.restoreRedis(roomType.getId(), nights, rooms);
            throw ex;
        }

        // ===== 5. 投递 TTL 延迟关单（失败可依赖定时补偿，不阻断下单） =====
        try {
            timeoutPublisher.sendDelayClose(order.getOrderNo());
        } catch (Exception ex) {
            log.warn("delay message failed, scheduler will close order {}: {}", order.getOrderNo(), ex.getMessage());
        }

        // ===== 6. 精确失效比价读缓存，避免脏报价 =====
        hotelReadCache.evictAfterInventoryChange(order.getHotelId(), order.getRoomTypeId(),
                order.getCheckIn(), order.getCheckOut(), order.getRooms());
        return toResponse(order);
    }

    /**
     * 事务内持久化：MySQL 权威扣减 → 生成待支付订单 → 写入每晚明细。
     * 注意：支付阶段不再扣库存，这里已经完成 hold。
     */
    private BookingOrder persistOrder(Long userId, CreateOrderRequest request, RoomType roomType,
                                      List<LocalDate> nights, List<RoomInventory> rows) {
        // ===== MySQL available >= n 条件更新，任一夜失败则整单回滚 =====
        inventoryService.deductMysql(roomType.getId(), nights, request.getRooms());
        BigDecimal amount = rows.stream()
                .map(r -> r.getPrice().multiply(BigDecimal.valueOf(request.getRooms())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BookingOrder order = new BookingOrder();
        order.setOrderNo(nextOrderNo());
        order.setRequestId(request.getRequestId());
        order.setUserId(userId);
        order.setHotelId(roomType.getHotelId());
        order.setRoomTypeId(roomType.getId());
        order.setCheckIn(request.getCheckIn());
        order.setCheckOut(request.getCheckOut());
        order.setNights(nights.size());
        order.setRooms(request.getRooms());
        order.setAmount(amount);
        order.setPenaltyAmount(BigDecimal.ZERO);
        order.setStatus(OrderStatus.PENDING_PAY);
        order.setExpireTime(LocalDateTime.now().plusMinutes(properties.getOrder().getPayTimeoutMinutes()));
        orderMapper.insert(order);
        for (RoomInventory row : rows) {
            OrderNight night = new OrderNight();
            night.setOrderId(order.getId());
            night.setStayDate(row.getStayDate());
            night.setRooms(request.getRooms());
            night.setPrice(row.getPrice());
            orderNightMapper.insert(night);
        }
        return order;
    }

    /**
     * 支付：只做状态机 CAS（PENDING_PAY → CONFIRMED），不再扣库存。
     * 与超时关单并发时，CAS 失败则查最新状态，已确认则幂等返回。
     */
    public OrderResponse pay(Long userId, Long orderId) {
        BookingOrder order = requireOwned(userId, orderId);
        // ===== 幂等：已支付直接返回 =====
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return toResponse(order);
        }
        if (!order.getStatus().canTransitTo(OrderStatus.CONFIRMED)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "当前状态不能支付");
        }
        // ===== CAS：只有仍是待支付才能改成已确认 =====
        int cas = orderMapper.casStatus(order.getId(), OrderStatus.PENDING_PAY, OrderStatus.CONFIRMED, null, null);
        if (cas != 1) {
            BookingOrder latest = orderMapper.selectById(orderId);
            if (latest != null && latest.getStatus() == OrderStatus.CONFIRMED) {
                return toResponse(latest);
            }
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "支付失败，订单可能已关闭");
        }
        return toResponse(orderMapper.selectById(orderId));
    }

    public OrderResponse cancel(Long userId, Long orderId) {
        BookingOrder order = requireOwned(userId, orderId);
        if (order.getStatus() == OrderStatus.PENDING_PAY) {
            return closeOrCancel(order, OrderStatus.CANCELLED, "USER_CANCEL", BigDecimal.ZERO);
        }
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            BigDecimal penalty = penaltyOf(order);
            return closeOrCancel(order, OrderStatus.CANCELLED, penalty.signum() == 0 ? "FREE_CANCEL" : "PENALTY_CANCEL", penalty);
        }
        throw new BizException(ResultCode.ORDER_STATUS_INVALID, "当前状态不能取消");
    }

    /**
     * MQ 延迟消息回调：仅关闭仍未支付的订单。
     * 若用户已支付，此处直接空转（消息到达 ≠ 必须关单）。
     */
    public void closeIfUnpaid(String orderNo) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>().eq(BookingOrder::getOrderNo, orderNo));
        // ===== 幂等空转：已确认/已取消/已关闭都不处理 =====
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAY) {
            return;
        }
        closeOrCancel(order, OrderStatus.CLOSED, "PAY_TIMEOUT", BigDecimal.ZERO);
    }

    public int closeExpiredOrders() {
        List<BookingOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getStatus, OrderStatus.PENDING_PAY)
                .le(BookingOrder::getExpireTime, LocalDateTime.now()));
        expired.forEach(order -> closeOrCancel(order, OrderStatus.CLOSED, "PAY_TIMEOUT", BigDecimal.ZERO));
        return expired.size();
    }

    public List<OrderResponse> mine(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                        .eq(BookingOrder::getUserId, userId)
                        .orderByDesc(BookingOrder::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public OrderResponse detail(Long userId, Long orderId) {
        return toResponse(requireOwned(userId, orderId));
    }

    /**
     * 关单/取消共用：CAS 改状态成功后才回补 MySQL+Redis 库存并失效缓存。
     * CAS 失败说明别人已改状态，避免重复回补导致超卖反向（库存虚高）。
     */
    private OrderResponse closeOrCancel(BookingOrder order, OrderStatus next, String reason, BigDecimal penalty) {
        if (!order.getStatus().canTransitTo(next)) {
            return toResponse(order);
        }
        List<LocalDate> nights = StayDates.occupiedNights(order.getCheckIn(), order.getCheckOut());
        Boolean changed = transactionTemplate.execute(status -> {
            // ===== 1. CAS 推进状态 =====
            int cas = orderMapper.casStatus(order.getId(), order.getStatus(), next, reason, penalty);
            if (cas != 1) {
                return false;
            }
            // ===== 2. 事务内回补 MySQL 日历库存 =====
            inventoryService.restoreMysql(order.getRoomTypeId(), nights, order.getRooms());
            return true;
        });
        if (Boolean.TRUE.equals(changed)) {
            // ===== 3. 事务外回补 Redis，并失效读缓存 =====
            inventoryService.restoreRedis(order.getRoomTypeId(), nights, order.getRooms());
            hotelReadCache.evictAfterInventoryChange(order.getHotelId(), order.getRoomTypeId(),
                    order.getCheckIn(), order.getCheckOut(), order.getRooms());
        }
        return toResponse(orderMapper.selectById(order.getId()));
    }

    private BigDecimal penaltyOf(BookingOrder order) {
        LocalDateTime deadline = order.getCheckIn().atStartOfDay().minusHours(properties.getOrder().getFreeCancelHours());
        if (LocalDateTime.now().isBefore(deadline)) {
            return BigDecimal.ZERO;
        }
        OrderNight first = orderNightMapper.selectOne(new LambdaQueryWrapper<OrderNight>()
                .eq(OrderNight::getOrderId, order.getId())
                .orderByAsc(OrderNight::getStayDate)
                .last("LIMIT 1"));
        if (first == null) {
            return order.getAmount();
        }
        return first.getPrice().multiply(BigDecimal.valueOf(order.getRooms()));
    }

    private BookingOrder requireOwned(Long userId, Long orderId) {
        BookingOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return order;
    }

    private OrderResponse toResponse(BookingOrder order) {
        Hotel hotel = hotelMapper.selectById(order.getHotelId());
        RoomType roomType = roomTypeMapper.selectById(order.getRoomTypeId());
        List<OrderNight> nights = orderNightMapper.selectList(new LambdaQueryWrapper<OrderNight>()
                .eq(OrderNight::getOrderId, order.getId())
                .orderByAsc(OrderNight::getStayDate));
        return OrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .requestId(order.getRequestId())
                .hotelId(order.getHotelId())
                .hotelName(hotel == null ? null : hotel.getName())
                .roomTypeId(order.getRoomTypeId())
                .roomTypeName(roomType == null ? null : roomType.getName())
                .checkIn(order.getCheckIn())
                .checkOut(order.getCheckOut())
                .nights(order.getNights())
                .rooms(order.getRooms())
                .amount(order.getAmount())
                .penaltyAmount(order.getPenaltyAmount())
                .status(order.getStatus())
                .expireTime(order.getExpireTime())
                .payTime(order.getPayTime())
                .cancelTime(order.getCancelTime())
                .cancelReason(order.getCancelReason())
                .nightsDetail(nights.stream().map(n -> OrderResponse.Night.builder()
                        .stayDate(n.getStayDate())
                        .price(n.getPrice())
                        .rooms(n.getRooms())
                        .build()).toList())
                .build();
    }

    private String nextOrderNo() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int rand = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "HB" + day + rand + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
