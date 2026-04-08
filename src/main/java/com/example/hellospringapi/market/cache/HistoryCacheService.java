package com.example.hellospringapi.market.cache;

import com.example.hellospringapi.market.model.Candle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class HistoryCacheService {

    private static final Logger log = LoggerFactory.getLogger(HistoryCacheService.class);
    private static final Duration TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public HistoryCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Candle> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached value for key={}: {}", key, e.getMessage());
            return null;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable on cache read for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    public void put(String key, List<Candle> candles) {
        try {
            String value = objectMapper.writeValueAsString(candles);
            redisTemplate.opsForValue().set(key, value, TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize candles for cache key={}: {}", key, e.getMessage());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable on cache write for key={}: {}", key, e.getMessage());
        }
    }
}
