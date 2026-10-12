package backend.gateway;

import backend.config.GateflowProperties;
import backend.keys.ApiKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final GateflowProperties properties;
    private final Map<String, Window> local = new ConcurrentHashMap<>();

    public RateLimitService(ObjectProvider<StringRedisTemplate> redis, GateflowProperties properties) {
        this.redis = redis.getIfAvailable();
        this.properties = properties;
    }

    public boolean allow(ApiKey key) {
        String bucket = "rl:" + key.getId() + ":" + (System.currentTimeMillis() / 60_000);
        if (redis == null) {
            return allowLocal(bucket, key.getQuotaPerMinute());
        }
        try {
            Long count = redis.opsForValue().increment(bucket);
            if (count != null && count == 1L) {
                redis.expire(bucket, Duration.ofMinutes(2));
            }
            return count != null && count <= key.getQuotaPerMinute();
        } catch (Exception ex) {
            if (!properties.isRateLimitFallbackLocal()) {
                throw ex;
            }
            return allowLocal(bucket, key.getQuotaPerMinute());
        }
    }

    private boolean allowLocal(String bucket, int quota) {
        Window window = local.computeIfAbsent(bucket, ignored -> new Window());
        return window.count.incrementAndGet() <= quota;
    }

    private static class Window {
        private final AtomicInteger count = new AtomicInteger();
    }
}
