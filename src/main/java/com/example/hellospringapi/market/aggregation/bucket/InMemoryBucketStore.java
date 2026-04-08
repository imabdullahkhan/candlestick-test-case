package com.example.hellospringapi.market.aggregation.bucket;

import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import com.example.hellospringapi.market.repository.CandleStorageRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryBucketStore implements BucketStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBucketStore.class);

    private final Map<SeriesKey, MutableCandle> activeCandles = new ConcurrentHashMap<>();

    @Override
    public AggregateResult aggregate(String symbol, CandleInterval interval, long bucketStart, double price) {
        SeriesKey key = new SeriesKey(symbol, interval);
        final AggregateResult[] holder = {AggregateResult.updated()};

        activeCandles.compute(key, (seriesKey, current) -> {
            if (current == null) {
                return MutableCandle.create(bucketStart, price);
            }

            if (bucketStart > current.time) {
                Candle finalized = current.toImmutable();
                log.debug("Candle finalized: symbol={} interval={} time={} O={} H={} L={} C={} V={}",
                        symbol, interval.value(), finalized.time(),
                        finalized.open(), finalized.high(), finalized.low(), finalized.close(), finalized.volume());
                holder[0] = AggregateResult.finalized(finalized);
                return MutableCandle.create(bucketStart, price);
            }

            if (bucketStart == current.time) {
                current.update(price);
                return current;
            }

            log.debug("Late event for closed bucket: symbol={} interval={} bucket={}", symbol, interval.value(), bucketStart);
            holder[0] = AggregateResult.lateEvent(bucketStart, price);
            return current;
        });

        return holder[0];
    }

    @Override
    public Optional<Candle> getActiveCandle(String symbol, CandleInterval interval) {
        MutableCandle active = activeCandles.get(new SeriesKey(symbol, interval));
        if (active != null) {
            return Optional.of(active.toImmutable());
        }
        return Optional.empty();
    }

    @Override
    public void flushAll(CandleStorageRepository repository) {
        log.info("Flushing {} in-memory active candle(s)...", activeCandles.size());
        activeCandles.forEach((key, mutableCandle) -> {
            try {
                Candle candle = mutableCandle.toImmutable();
                if (repository != null) {
                    repository.upsert(key.symbol(), key.interval(), candle);
                }
                log.debug("Flushed candle: symbol={} interval={} time={}", key.symbol(), key.interval().value(), candle.time());
            } catch (Exception e) {
                log.error("Failed to flush candle: symbol={} interval={}: {}", key.symbol(), key.interval().value(), e.getMessage());
            }
        });
        activeCandles.clear();
        log.info("In-memory candle flush complete.");
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
