package br.com.trsthales.telemetry.infrastructure.redis;

import br.com.trsthales.telemetry.application.port.PacketIdempotencyStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class RedisPacketIdempotencyStore implements PacketIdempotencyStore {

    static final String KEY_PREFIX = "telemetry:processed:";
    static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public RedisPacketIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String packetId) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + packetId, "1", TTL);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(String packetId) {
        redisTemplate.delete(KEY_PREFIX + packetId);
    }
}