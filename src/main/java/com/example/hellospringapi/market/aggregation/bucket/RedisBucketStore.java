package com.example.hellospringapi.market.aggregation.bucket;

import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import com.example.hellospringapi.market.repository.CandleStorageRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

@Service
public class RedisBucketStore implements BucketStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBucketStore.class);
    private static final String KEY_PREFIX = "bucket:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> aggregateScript;

    public RedisBucketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.aggregateScript = new DefaultRedisScript<>();
        this.aggregateScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("scripts/candle-aggregate.lua")));
        this.aggregateScript.setResultType(List.class);
    }

    @Override
    public AggregateResult aggregate(String symbol, CandleInterval interval, long bucketStart, double price) {
        String key = bucketKey(symbol, interval);
        try {
            @SuppressWarnings("unchecked")
            List<String> result = redisTemplate.execute(
                    aggregateScript,
                    Collections.singletonList(key),
                    String.valueOf(bucketStart),
                    String.valueOf(price)
            );

            if (result == null || result.isEmpty()) {
                return AggregateResult.updated();
            }

            if ("LATE".equals(result.get(0))) {
                long lateBucket = Long.parseLong(result.get(1));
                double latePrice = Double.parseDouble(result.get(2));
                log.debug("Late event via Redis: symbol={} interval={} bucket={}", symbol, interval.value(), lateBucket);
                return AggregateResult.lateEvent(lateBucket, latePrice);
            }

            Candle finalized = parseCandle(result);
            log.debug("Candle finalized via Redis: symbol={} interval={} time={} O={} H={} L={} C={} V={}",
                    symbol, interval.value(), finalized.time(),
                    finalized.open(), finalized.high(), finalized.low(), finalized.close(), finalized.volume());
            return AggregateResult.finalized(finalized);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for bucket aggregation: symbol={} interval={}: {}", symbol, interval.value(), e.getMessage());
            return AggregateResult.unavailable();
        } catch (Exception e) {
            log.error("Unexpected error during Redis aggregation: symbol={} interval={}", symbol, interval.value(), e);
            return AggregateResult.unavailable();
        }
    }

    @Override
    public Optional<Candle> getActiveCandle(String symbol, CandleInterval interval) {
        String key = bucketKey(symbol, interval);
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
            if (fields.isEmpty() || !fields.containsKey("time")) {
                return Optional.empty();
            }
            return Optional.of(new Candle(
                    Long.parseLong((String) fields.get("time")),
                    Double.parseDouble((String) fields.get("open")),
                    Double.parseDouble((String) fields.get("high")),
                    Double.parseDouble((String) fields.get("low")),
                    Double.parseDouble((String) fields.get("close")),
                    Long.parseLong((String) fields.get("volume"))
            ));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable for active candle read: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Unexpected error reading active candle from Redis", e);
            return Optional.empty();
        }
    }

    @Override
    public void flushAll(CandleStorageRepository repository) {
        try {
            var keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            log.info("Flushing {} active Redis bucket(s) to DB...", keys.size());
            for (String key : keys) {
                try {
                    Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
                    if (fields.isEmpty() || !fields.containsKey("time")) continue;

                    Candle candle = new Candle(
                            Long.parseLong((String) fields.get("time")),
                            Double.parseDouble((String) fields.get("open")),
                            Double.parseDouble((String) fields.get("high")),
                            Double.parseDouble((String) fields.get("low")),
                            Double.parseDouble((String) fields.get("close")),
                            Long.parseLong((String) fields.get("volume"))
                    );

                    String[] parts = key.replace(KEY_PREFIX, "").split(":");
                    if (parts.length == 2) {
                        CandleInterval interval = CandleInterval.fromValue(parts[1]);
                        repository.upsert(parts[0], interval, candle);
                        log.debug("Flushed Redis bucket to DB: key={}", key);
                    }
                } catch (Exception e) {
                    log.error("Failed to flush Redis bucket key={}: {}", key, e.getMessage());
                }
            }
            log.info("Redis bucket flush to DB complete.");
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during flush: {}", e.getMessage());
        }
    }

    private Candle parseCandle(List<String> result) {
        return new Candle(
                Long.parseLong(result.get(0)),
                Double.parseDouble(result.get(1)),
                Double.parseDouble(result.get(2)),
                Double.parseDouble(result.get(3)),
                Double.parseDouble(result.get(4)),
                Long.parseLong(result.get(5))
        );
    }

    private String bucketKey(String symbol, CandleInterval interval) {
        return KEY_PREFIX + symbol + ":" + interval.value();
    }
}
