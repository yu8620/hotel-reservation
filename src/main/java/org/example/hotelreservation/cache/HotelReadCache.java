package org.example.hotelreservation.cache;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.HotelProperties;
import org.example.hotelreservation.dto.HotelCardResponse;
import org.example.hotelreservation.dto.PageResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 比价读缓存网关（Cache Aside）。
 * <p>只服务搜索/静态/报价/日历读路径；不参与 Lua 扣库存，下单成功后通过 evict 精确失效。
 * 面试对应：穿透（空对象短 TTL）、雪崩（TTL 抖动）、与扣减路径解耦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HotelReadCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper objectMapper;
    private final HotelProperties properties;

    public boolean enabled() {
        return properties.getCache() != null && properties.getCache().isEnabled();
    }

    public Optional<PageResponse<HotelCardResponse>> getSearch(String key) {
        return read(key, new TypeReference<PageResponse<HotelCardResponse>>() {});
    }

    public void putSearch(String key, PageResponse<HotelCardResponse> page) {
        write(key, page, properties.getCache().getSearchTtlSeconds());
    }

    public Optional<HotelStaticSnapshot> getStatic(Long hotelId) {
        return read(HotelCacheKeys.hotelStatic(hotelId), HotelStaticSnapshot.class);
    }

    public void putStatic(HotelStaticSnapshot snapshot) {
        if (snapshot == null || snapshot.getId() == null) {
            return;
        }
        int ttl = snapshot.isMissing()
                ? properties.getCache().getNullObjectTtlSeconds()
                : properties.getCache().getStaticTtlSeconds();
        write(HotelCacheKeys.hotelStatic(snapshot.getId()), snapshot, ttl);
    }

    /** 防穿透：缓存短 TTL 空对象，避免假 hotelId 反复打 MySQL。 */
    public void putStaticMissing(Long hotelId) {
        if (hotelId == null) {
            return;
        }
        putStatic(HotelStaticSnapshot.builder().id(hotelId).missing(true).build());
    }

    public Map<Long, HotelQuoteSnapshot> mgetQuotes(List<Long> hotelIds, LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (!enabled() || hotelIds == null || hotelIds.isEmpty() || checkIn == null || checkOut == null) {
            return Map.of();
        }
        try {
            List<String> keys = new ArrayList<>(hotelIds.size());
            for (Long id : hotelIds) {
                keys.add(HotelCacheKeys.quote(id, checkIn, checkOut, rooms));
            }
            List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }
            Map<Long, HotelQuoteSnapshot> result = new HashMap<>();
            for (int i = 0; i < hotelIds.size(); i++) {
                String raw = values.get(i);
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                HotelQuoteSnapshot quote = objectMapper.readValue(raw, HotelQuoteSnapshot.class);
                result.put(hotelIds.get(i), quote);
            }
            return result;
        } catch (Exception ex) {
            log.warn("mgetQuotes failed: {}", ex.getMessage());
            return Map.of();
        }
    }

    public void putQuote(Long hotelId, LocalDate checkIn, LocalDate checkOut, int rooms, HotelQuoteSnapshot quote) {
        if (hotelId == null || checkIn == null || checkOut == null || quote == null) {
            return;
        }
        write(HotelCacheKeys.quote(hotelId, checkIn, checkOut, rooms), quote, properties.getCache().getQuoteTtlSeconds());
    }

    public Optional<CalendarSnapshot> getCalendar(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        if (roomTypeId == null || checkIn == null || checkOut == null) {
            return Optional.empty();
        }
        return read(HotelCacheKeys.calendar(roomTypeId, checkIn, checkOut), CalendarSnapshot.class);
    }

    public void putCalendar(CalendarSnapshot snapshot, LocalDate checkIn, LocalDate checkOut) {
        if (snapshot == null || snapshot.getRoomTypeId() == null || checkIn == null || checkOut == null) {
            return;
        }
        write(HotelCacheKeys.calendar(snapshot.getRoomTypeId(), checkIn, checkOut), snapshot,
                properties.getCache().getCalendarTtlSeconds());
    }

    /**
     * 下单/关单后精确失效报价与日历缓存，避免读到旧库存。
     * 不删静态酒店信息（与库存无关）。
     */
    public void evictAfterInventoryChange(Long hotelId, Long roomTypeId, LocalDate checkIn, LocalDate checkOut, int rooms) {
        if (!enabled()) {
            return;
        }
        try {
            // ===== 组装需要删除的 quote/calendar key =====
            List<String> keys = new ArrayList<>();
            if (hotelId != null && checkIn != null && checkOut != null) {
                keys.add(HotelCacheKeys.quote(hotelId, checkIn, checkOut, rooms));
                // common room counts when browsing
                if (rooms != 1) {
                    keys.add(HotelCacheKeys.quote(hotelId, checkIn, checkOut, 1));
                }
                if (rooms != 2) {
                    keys.add(HotelCacheKeys.quote(hotelId, checkIn, checkOut, 2));
                }
            }
            if (roomTypeId != null && checkIn != null && checkOut != null) {
                keys.add(HotelCacheKeys.calendar(roomTypeId, checkIn, checkOut));
            }
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.warn("evictAfterInventoryChange failed: {}", ex.getMessage());
        }
    }

    private <T> Optional<T> read(String key, Class<T> type) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception ex) {
            log.warn("cache read {} failed: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        if (!enabled()) {
            return Optional.empty();
        }
        try {
            String raw = stringRedisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, type));
        } catch (Exception ex) {
            log.warn("cache read {} failed: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写入缓存：TTL 加随机抖动，降低同一时刻大批 key 一起过期（防雪崩）。
     */
    private void write(String key, Object value, int ttlSeconds) {
        // ===== 序列化并 SET，TTL = 配置值 ± 抖动 =====
        if (!enabled() || value == null || ttlSeconds <= 0) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            stringRedisTemplate.opsForValue().set(key, json, Duration.ofSeconds(withJitter(ttlSeconds)));
        } catch (Exception ex) {
            log.warn("cache write {} failed: {}", key, ex.getMessage());
        }
    }

    /** 防雪崩：实际 TTL = 基础值 + [0, jitter] 随机秒。 */
    private int withJitter(int baseSeconds) {
        int jitter = properties.getCache().getTtlJitterSeconds();
        if (jitter <= 0) {
            return baseSeconds;
        }
        return baseSeconds + ThreadLocalRandom.current().nextInt(jitter + 1);
    }
}