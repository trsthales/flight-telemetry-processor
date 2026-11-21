package br.com.trsthales.telemetry.infrastructure.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Import(RedisPacketIdempotencyStore.class)
@Testcontainers
class RedisPacketIdempotencyStoreTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisPacketIdempotencyStore store;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanUp() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void acquiresFirstPacketWith24HourTtlAndRejectsDuplicatesUntilReleased() {
        assertThat(store.tryAcquire("pkt-100")).isTrue();
        assertThat(store.tryAcquire("pkt-100")).isFalse();

        Long ttl = redisTemplate.getExpire(RedisPacketIdempotencyStore.KEY_PREFIX + "pkt-100", TimeUnit.SECONDS);
        assertThat(ttl).isBetween(86390L, 86400L);

        store.release("pkt-100");
        assertThat(store.tryAcquire("pkt-100")).isTrue();
    }
}