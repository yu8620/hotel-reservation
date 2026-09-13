package org.example.hotelreservation.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.hotelreservation.config.HotelProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Redis mutex for cache rebuild (anti-breakdown / hot-key stampede).
 * Only the lock holder rebuilds; others wait briefly and re-read cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheMutex {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final HotelProperties properties;

    public boolean tryLock(String lockKey) {
        if (!cacheEnabled()) {
            return true;
        }
        try {
            String token = UUID.randomUUID().toString();
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, Duration.ofSeconds(lockTtlSeconds()));
            if (Boolean.TRUE.equals(ok)) {
                // stash token in thread-local would be cleaner; encode token in key holder via ThreadLocal
                LockTokenHolder.set(lockKey, token);
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("tryLock {} failed, rebuild without lock: {}", lockKey, ex.getMessage());
            return true;
        }
    }

    public void unlock(String lockKey) {
        if (!cacheEnabled()) {
            return;
        }
        String token = LockTokenHolder.get(lockKey);
        LockTokenHolder.clear(lockKey);
        if (token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), token);
        } catch (Exception ex) {
            log.warn("unlock {} failed: {}", lockKey, ex.getMessage());
        }
    }

    /**
     * Rebuild under mutex: double-check cache after acquiring lock / after wait.
     */
    public <T> T loadThrough(String lockKey, Supplier<T> cacheGet, Callable<T> loader, java.util.function.Consumer<T> cachePut) {
        T hit = cacheGet.get();
        if (hit != null) {
            return hit;
        }
        boolean locked = tryLock(lockKey);
        try {
            if (locked) {
                hit = cacheGet.get();
                if (hit != null) {
                    return hit;
                }
                T loaded = loader.call();
                if (loaded != null) {
                    cachePut.accept(loaded);
                }
                return loaded;
            }
            // someone else is rebuilding — wait and re-read
            int waitMs = waitMillis();
            int retries = Math.max(1, properties.getCache().getLockWaitRetries());
            for (int i = 0; i < retries; i++) {
                sleepQuietly(waitMs);
                hit = cacheGet.get();
                if (hit != null) {
                    return hit;
                }
            }
            // fallback: rebuild without lock to avoid hanging the request
            T loaded = loader.call();
            if (loaded != null) {
                cachePut.accept(loaded);
            }
            return loaded;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cache rebuild failed", ex);
        } finally {
            if (locked) {
                unlock(lockKey);
            }
        }
    }

    private boolean cacheEnabled() {
        return properties.getCache() != null && properties.getCache().isEnabled();
    }

    private int lockTtlSeconds() {
        int ttl = properties.getCache().getLockTtlSeconds();
        return ttl > 0 ? ttl : 5;
    }

    private int waitMillis() {
        int ms = properties.getCache().getLockWaitMillis();
        return ms > 0 ? ms : 50;
    }

    private static void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thread-local lock token so unlock deletes only our lock. */
    static final class LockTokenHolder {
        private static final ThreadLocal<java.util.Map<String, String>> TOKENS =
                ThreadLocal.withInitial(java.util.HashMap::new);

        private LockTokenHolder() {
        }

        static void set(String key, String token) {
            TOKENS.get().put(key, token);
        }

        static String get(String key) {
            return TOKENS.get().get(key);
        }

        static void clear(String key) {
            TOKENS.get().remove(key);
            if (TOKENS.get().isEmpty()) {
                TOKENS.remove();
            }
        }
    }
}