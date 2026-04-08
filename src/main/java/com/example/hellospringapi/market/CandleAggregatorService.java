package com.example.hellospringapi.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.stereotype.Service;

@Service
public class CandleAggregatorService {

    private final Map<SeriesKey, MutableCandle> activeCandles = new ConcurrentHashMap<>();
    private final Map<SeriesKey, ConcurrentSkipListMap<Long, Candle>> candleStore = new ConcurrentHashMap<>();
    private final CandleStorageRepository candleStorageRepository;
    private final HistoryCacheService historyCacheService;

    public CandleAggregatorService(
            CandleStorageRepository candleStorageRepository,
            HistoryCacheService historyCacheService
    ) {
        this.candleStorageRepository = candleStorageRepository;
        this.historyCacheService = historyCacheService;
    }

    public void onEvent(BidAskEvent event, List<CandleInterval> intervals) {
        for (CandleInterval interval : intervals) {
            aggregateForInterval(event, interval);
        }
    }

    public List<Candle> getHistory(String symbol, CandleInterval interval, long from, long to) {
        String cacheKey = cacheKey(symbol, interval, from, to);
        if (historyCacheService != null) {
            List<Candle> cached = historyCacheService.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        SeriesKey key = new SeriesKey(symbol, interval);
        List<Candle> result = new ArrayList<>();
        if (candleStorageRepository != null) {
            result.addAll(candleStorageRepository.findHistory(symbol, interval, from, to));
        } else {
            ConcurrentSkipListMap<Long, Candle> history = candleStore.get(key);
            if (history != null) {
                result.addAll(history.subMap(from, true, to, true).values());
            }
        }

        MutableCandle active = activeCandles.get(key);
        if (active != null && active.time >= from && active.time <= to) {
            result.removeIf(c -> c.time() == active.time);
            result.add(active.toImmutable());
        }

        result.sort(Comparator.comparingLong(Candle::time));
        if (historyCacheService != null) {
            historyCacheService.put(cacheKey, result);
        }
        return result;
    }

    private void aggregateForInterval(BidAskEvent event, CandleInterval interval) {
        SeriesKey key = new SeriesKey(event.symbol(), interval);
        long bucketStart = interval.bucketStart(event.timestamp());
        double price = midPrice(event);

        activeCandles.compute(key, (seriesKey, current) -> {
            if (current == null) {
                return MutableCandle.create(bucketStart, price);
            }

            if (bucketStart > current.time) {
                saveFinalized(seriesKey, current.toImmutable());
                return MutableCandle.create(bucketStart, price);
            }

            if (bucketStart == current.time) {
                current.update(price);
                return current;
            }

            // Late event for an already-closed bucket: update historical candle.
            candleStore
                    .computeIfAbsent(seriesKey, ignored -> new ConcurrentSkipListMap<>())
                    .compute(bucketStart, (ignored, existing) -> {
                        Candle updated = updateHistoric(existing, bucketStart, price);
                        persistIfEnabled(seriesKey, updated);
                        return updated;
                    });
            return current;
        });
    }

    private Candle updateHistoric(Candle existing, long bucketStart, double price) {
        if (existing == null) {
            return new Candle(bucketStart, price, price, price, price, 1L);
        }
        return new Candle(
                existing.time(),
                existing.open(),
                Math.max(existing.high(), price),
                Math.min(existing.low(), price),
                price,
                existing.volume() + 1
        );
    }

    private void saveFinalized(SeriesKey key, Candle candle) {
        candleStore.computeIfAbsent(key, ignored -> new ConcurrentSkipListMap<>())
                .put(candle.time(), candle);
        persistIfEnabled(key, candle);
    }

    private void persistIfEnabled(SeriesKey key, Candle candle) {
        if (candleStorageRepository != null) {
            candleStorageRepository.upsert(key.symbol(), key.interval(), candle);
        }
    }

    private String cacheKey(String symbol, CandleInterval interval, long from, long to) {
        return "history:%s:%s:%d:%d".formatted(symbol, interval.value(), from, to);
    }

    private double midPrice(BidAskEvent event) {
        return (event.bid() + event.ask()) / 2.0;
    }

    private record SeriesKey(String symbol, CandleInterval interval) {
    }

    private static final class MutableCandle {
        private final long time;
        private final double open;
        private double high;
        private double low;
        private double close;
        private long volume;

        private MutableCandle(long time, double open) {
            this.time = time;
            this.open = open;
            this.high = open;
            this.low = open;
            this.close = open;
            this.volume = 1L;
        }

        static MutableCandle create(long time, double price) {
            return new MutableCandle(time, price);
        }

        void update(double price) {
            this.high = Math.max(this.high, price);
            this.low = Math.min(this.low, price);
            this.close = price;
            this.volume++;
        }

        Candle toImmutable() {
            return new Candle(time, open, high, low, close, volume);
        }
    }
}
