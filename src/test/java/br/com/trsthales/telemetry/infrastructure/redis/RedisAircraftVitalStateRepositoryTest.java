package br.com.trsthales.telemetry.infrastructure.redis;

import br.com.trsthales.telemetry.domain.AircraftVitalState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataRedisTest
@Import({RedisAircraftVitalStateRepository.class, JacksonAutoConfiguration.class})
@Testcontainers
class RedisAircraftVitalStateRepositoryTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RedisAircraftVitalStateRepository repository;

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
    void persistsNewerStatesAndDiscardsOlderOrSameMillisecondTies() {
        AircraftVitalState firstAtT2 = stateAt("2026-11-21T16:30:00.500Z", "850.00");
        AircraftVitalState tieAtT2DifferentTemp = stateAt("2026-11-21T16:30:00.500Z", "995.00");
        AircraftVitalState olderT1 = stateAt("2026-11-21T16:29:59.999Z", "910.00");
        AircraftVitalState newerT3 = stateAt("2026-11-21T16:30:00.501Z", "860.00");

        assertThat(repository.saveIfNewer(firstAtT2)).isTrue();
        assertThat(repository.saveIfNewer(tieAtT2DifferentTemp)).isFalse();
        assertThat(repository.saveIfNewer(olderT1)).isFalse();

        assertThat(repository.findLatestByAircraftId("PR-XMA"))
                .isPresent()
                .get()
                .extracting(AircraftVitalState::engineTempCelsius)
                .isEqualTo(new BigDecimal("850.00"));

        assertThat(repository.saveIfNewer(newerT3)).isTrue();
        assertThat(repository.findLatestByAircraftId("PR-XMA"))
                .isPresent()
                .get()
                .extracting(AircraftVitalState::recordedAt, AircraftVitalState::engineTempCelsius)
                .containsExactly(Instant.parse("2026-11-21T16:30:00.501Z"), new BigDecimal("860.00"));
    }

    @Test
    void setsSixHourTtlAndDoesNotRenewTtlWhenPacketIsDiscarded() {
        String key = RedisAircraftVitalStateRepository.KEY_PREFIX + "PR-XMA";
        AircraftVitalState current = stateAt("2026-11-21T16:30:00Z", "850.00");
        AircraftVitalState stale = stateAt("2026-11-21T16:25:00Z", "840.00");

        assertThat(repository.saveIfNewer(current)).isTrue();
        Long initialTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(initialTtl).isBetween(21590L, 21600L);

        redisTemplate.expire(key, Duration.ofSeconds(120));
        assertThat(repository.saveIfNewer(stale)).isFalse();

        Long ttlAfterDiscard = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttlAfterDiscard).isLessThanOrEqualTo(120L);
    }

    @Test
    void translatesCorruptedRedisPayloadIntoDataAccessException() {
        String key = RedisAircraftVitalStateRepository.KEY_PREFIX + "PR-XMA";
        redisTemplate.opsForHash().put(key, RedisAircraftVitalStateRepository.FIELD_PAYLOAD, "{corrupted-json");

        assertThatThrownBy(() -> repository.findLatestByAircraftId("PR-XMA"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("Corrupted vital state payload");
    }

    @Test
    void keepsNewestStateUnderConcurrentOutOfOrderUpdates() throws Exception {
        Instant base = Instant.parse("2026-11-21T16:00:00Z");
        int totalPackets = 50;

        List<AircraftVitalState> packets = new ArrayList<>(totalPackets);
        for (int i = 1; i <= totalPackets; i++) {
            packets.add(stateAt(base.plusMillis(i * 10L).toString(), "800.00"));
        }
        Collections.shuffle(packets);

        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>(totalPackets);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (AircraftVitalState packet : packets) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return repository.saveIfNewer(packet);
                }));
            }

            startGate.countDown();
            for (Future<Boolean> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        }

        assertThat(repository.findLatestByAircraftId("PR-XMA"))
                .isPresent()
                .get()
                .extracting(AircraftVitalState::recordedAt)
                .isEqualTo(base.plusMillis(totalPackets * 10L));
    }

    private AircraftVitalState stateAt(String isoTime, String temp) {
        return AircraftVitalState.of(
                "PR-XMA",
                "AD4021",
                Instant.parse(isoTime),
                new BigDecimal(temp),
                new BigDecimal("4000.00"),
                new BigDecimal("11.30")
        );
    }
}