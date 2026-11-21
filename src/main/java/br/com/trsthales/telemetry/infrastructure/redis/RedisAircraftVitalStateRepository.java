package br.com.trsthales.telemetry.infrastructure.redis;

import br.com.trsthales.telemetry.application.port.AircraftVitalStateRepository;
import br.com.trsthales.telemetry.domain.AircraftVitalState;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
public class RedisAircraftVitalStateRepository implements AircraftVitalStateRepository {

    static final String KEY_PREFIX = "aircraft:vitals:";
    static final String FIELD_PAYLOAD = "payload";
    static final long STATE_TTL_SECONDS = Duration.ofHours(6).toSeconds();

    private static final String COMPARE_AND_SET_LUA = """
            local currentEpoch = redis.call('HGET', KEYS[1], 'recordedAtEpochMs')
            local incomingEpoch = tonumber(ARGV[1])
            if currentEpoch and tonumber(currentEpoch) >= incomingEpoch then
                return 0
            end
            redis.call('HSET', KEYS[1], 'recordedAtEpochMs', ARGV[1], 'payload', ARGV[2])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3]))
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final DefaultRedisScript<Long> compareAndSetScript;

    public RedisAircraftVitalStateRepository(StringRedisTemplate redisTemplate, JsonMapper jsonMapper) {
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.compareAndSetScript = new DefaultRedisScript<>(COMPARE_AND_SET_LUA, Long.class);
    }

    @Override
    public boolean saveIfNewer(AircraftVitalState state) {
        String key = KEY_PREFIX + state.aircraftId();
        String payload = serialize(state);

        Long result = redisTemplate.execute(
                compareAndSetScript,
                List.of(key),
                String.valueOf(state.recordedAt().toEpochMilli()),
                payload,
                String.valueOf(STATE_TTL_SECONDS)
        );
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public Optional<AircraftVitalState> findLatestByAircraftId(String aircraftId) {
        Object rawPayload = redisTemplate.opsForHash().get(KEY_PREFIX + aircraftId, FIELD_PAYLOAD);
        if (rawPayload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(rawPayload.toString(), AircraftVitalState.class));
        } catch (JacksonException ex) {
            throw new InvalidDataAccessResourceUsageException(
                    "Corrupted vital state payload in Redis for aircraft " + aircraftId, ex
            );
        }
    }

    private String serialize(AircraftVitalState state) {
        try {
            return jsonMapper.writeValueAsString(state);
        } catch (JacksonException ex) {
            throw new InvalidDataAccessResourceUsageException("Failed to serialize vital state", ex);
        }
    }
}