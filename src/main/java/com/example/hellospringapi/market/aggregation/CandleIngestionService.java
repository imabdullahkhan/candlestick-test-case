package com.example.hellospringapi.market.aggregation;

import com.example.hellospringapi.market.aggregation.bucket.BucketStore;
import com.example.hellospringapi.market.model.BidAskEvent;
import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import com.example.hellospringapi.market.repository.CandleStorageRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CandleIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CandleIngestionService.class);

    private final Map<SeriesKey, ConcurrentSkipListMap<Long, Candle>> candleStore = new ConcurrentHashMap<>();
    private final BucketStore bucketStore;
    private final CandleStorageRepository candleStorageRepository;

    public CandleIngestionService(BucketStore bucketStore, CandleStorageRepository candleStorageRepository) {
        this.bucketStore = bucketStore;
        this.candleStorageRepository = candleStorageRepository;
    }

    public void onEvent(BidAskEvent event, List<CandleInterval> intervals) {
        log.trace("Received event: symbol={} bid={} ask={} ts={}",
                event.symbol(), event.bid(), event.ask(), event.timestamp());
        for (CandleInterval interval : intervals) {
            aggregateForInterval(event, interval);
        }
    }

    public void flushActiveCandles() {
        bucketStore.flushAll(candleStorageRepository);
    }

    public Optional<Candle> getActiveCandle(String symbol, CandleInterval interval) {
        return bucketStore.getActiveCandle(symbol, interval);
    }

    public List<Candle> getFinalizedCandles(String symbol, CandleInterval interval, long from, long to) {
        SeriesKey key = new SeriesKey(symbol, interval);
        ConcurrentSkipListMap<Long, Candle> history = candleStore.get(key);
        if (history == null) {
            return List.of();
        }
        return List.copyOf(history.subMap(from, true, to, true).values());
    }

    private void aggregateForInterval(BidAskEvent event, CandleInterval interval) {
        String symbol = event.symbol();
        long bucketStart = interval.bucketStart(event.timestamp());
        double price = midPrice(event);

        BucketStore.AggregateResult result = bucketStore.aggregate(symbol, interval, bucketStart, price);

        switch (result.type()) {
            case FINALIZED -> {
                Candle finalized = result.finalized();
                candleStore.computeIfAbsent(new SeriesKey(symbol, interval), k -> new ConcurrentSkipListMap<>())
                        .put(finalized.time(), finalized);
                persistSafely(symbol, interval, finalized);
            }
            case LATE_EVENT -> {
                SeriesKey key = new SeriesKey(symbol, interval);
                Candle updated = candleStore
                        .computeIfAbsent(key, k -> new ConcurrentSkipListMap<>())
                        .compute(result.lateBucket(), (ts, existing) -> updateHistoric(existing, result.lateBucket(), result.latePrice()));
                persistSafely(symbol, interval, updated);
            }
            case UPDATED, UNAVAILABLE -> { }
        }
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

    private void persistSafely(String symbol, CandleInterval interval, Candle candle) {
        if (candleStorageRepository == null) return;
        try {
            candleStorageRepository.upsert(symbol, interval, candle);
        } catch (Exception e) {
            log.error("Failed to persist candle: symbol={} interval={} time={}: {}",
                    symbol, interval.value(), candle.time(), e.getMessage());
        }
    }

    private double midPrice(BidAskEvent event) {
        return (event.bid() + event.ask()) / 2.0;
    }

    record SeriesKey(String symbol, CandleInterval interval) {
    }
}
