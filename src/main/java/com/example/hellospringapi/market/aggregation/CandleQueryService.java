package com.example.hellospringapi.market.aggregation;

import com.example.hellospringapi.market.cache.HistoryCacheService;
import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import com.example.hellospringapi.market.repository.CandleStorageRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CandleQueryService {

    private static final Logger log = LoggerFactory.getLogger(CandleQueryService.class);

    private final CandleIngestionService ingestionService;
    private final CandleStorageRepository candleStorageRepository;
    private final HistoryCacheService historyCacheService;

    public CandleQueryService(
            CandleIngestionService ingestionService,
            CandleStorageRepository candleStorageRepository,
            HistoryCacheService historyCacheService
    ) {
        this.ingestionService = ingestionService;
        this.candleStorageRepository = candleStorageRepository;
        this.historyCacheService = historyCacheService;
    }

    public List<Candle> getHistory(String symbol, CandleInterval interval, long from, long to) {
        String cacheKey = cacheKey(symbol, interval, from, to);
        if (historyCacheService != null) {
            List<Candle> cached = historyCacheService.get(cacheKey);
            if (cached != null) {
                log.debug("Cache hit for history: symbol={} interval={} from={} to={}", symbol, interval.value(), from, to);
                return cached;
            }
        }

        List<Candle> result = new ArrayList<>();
        if (candleStorageRepository != null) {
            try {
                result.addAll(candleStorageRepository.findHistory(symbol, interval, from, to));
            } catch (Exception e) {
                log.error("Failed to query candles from DB: symbol={} interval={}: {}", symbol, interval.value(), e.getMessage());
            }
        } else {
            result.addAll(ingestionService.getFinalizedCandles(symbol, interval, from, to));
        }

        Optional<Candle> active = ingestionService.getActiveCandle(symbol, interval);
        if (active.isPresent() && active.get().time() >= from && active.get().time() <= to) {
            long activeTime = active.get().time();
            result.removeIf(c -> c.time() == activeTime);
            result.add(active.get());
        }

        result.sort(Comparator.comparingLong(Candle::time));
        if (historyCacheService != null) {
            historyCacheService.put(cacheKey, result);
        }
        log.debug("History query: symbol={} interval={} from={} to={} candles={}", symbol, interval.value(), from, to, result.size());
        return result;
    }

    private String cacheKey(String symbol, CandleInterval interval, long from, long to) {
        return "history:%s:%s:%d:%d".formatted(symbol, interval.value(), from, to);
    }
}
