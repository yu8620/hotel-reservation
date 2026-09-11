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
public class OrderService {

    private final BookingOrderMapper orderMapper;
    private final OrderNightMapper orderNightMapper;
    private final RoomTypeMapper roomTypeMapper;
    private final HotelMapper hotelMapper;
    private final InventoryService inventoryService;
    private final OrderTimeoutPublisher timeoutPublisher;
    private final HotelProperties properties;
    private final TransactionTemplate transactionTemplate;

    public OrderResponse create(Long userId, CreateOrderRequest request) {
        BookingOrder existed = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getRequestId, request.getRequestId()));
        if (existed != null) {
            return toResponse(existed);
        }
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
        boolean redisOk = inventoryService.tryDeductRedis(roomType.getId(), nights, rooms);
        if (!redisOk) {
            throw new BizException(ResultCode.SOLD_OUT);
        }
        BookingOrder order;
        try {
            order = transactionTemplate.execute(status -> persistOrder(userId, request, roomType, nights, rows));
        } catch (DuplicateKeyException duplicate) {
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
        try {
            timeoutPublisher.sendDelayClose(order.getOrderNo());
        } catch (Exception ex) {
            log.warn("delay message failed, scheduler will close order {}: {}", order.getOrderNo(), ex.getMessage());
        }
        return toResponse(order);
    }

    private BookingOrder persistOrder(Long userId, CreateOrderRequest request, RoomType roomType,
                                      List<LocalDate> nights, List<RoomInventory> rows) {
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

    public OrderResponse pay(Long userId, Long orderId) {
        BookingOrder order = requireOwned(userId, orderId);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return toResponse(order);
        }
        if (!order.getStatus().canTransitTo(OrderStatus.CONFIRMED)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "当前状态不能支付");
        }
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

    public void closeIfUnpaid(String orderNo) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>().eq(BookingOrder::getOrderNo, orderNo));
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

    private OrderResponse closeOrCancel(BookingOrder order, OrderStatus next, String reason, BigDecimal penalty) {
        if (!order.getStatus().canTransitTo(next)) {
            return toResponse(order);
        }
        List<LocalDate> nights = StayDates.occupiedNights(order.getCheckIn(), order.getCheckOut());
        Boolean changed = transactionTemplate.execute(status -> {
            int cas = orderMapper.casStatus(order.getId(), order.getStatus(), next, reason, penalty);
            if (cas != 1) {
                return false;
            }
            inventoryService.restoreMysql(order.getRoomTypeId(), nights, order.getRooms());
            return true;
        });
        if (Boolean.TRUE.equals(changed)) {
            inventoryService.restoreRedis(order.getRoomTypeId(), nights, order.getRooms());
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
