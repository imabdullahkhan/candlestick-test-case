package com.example.hellospringapi.market;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class HistoryCacheService {

    private static final Duration TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public HistoryCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Candle> get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public void put(String key, List<Candle> candles) {
        try {
            String value = objectMapper.writeValueAsString(candles);
            redisTemplate.opsForValue().set(key, value, TTL);
        } catch (JsonProcessingException ignored) {
            // Cache should never break primary API path.
        }
    }
}
