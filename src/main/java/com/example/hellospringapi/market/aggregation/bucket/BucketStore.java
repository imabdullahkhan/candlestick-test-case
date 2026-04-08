package com.example.hellospringapi.market.aggregation.bucket;

import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import com.example.hellospringapi.market.repository.CandleStorageRepository;
import java.util.Optional;

public interface BucketStore {

    AggregateResult aggregate(String symbol, CandleInterval interval, long bucketStart, double price);

    Optional<Candle> getActiveCandle(String symbol, CandleInterval interval);

    void flushAll(CandleStorageRepository repository);

    record AggregateResult(Type type, Candle finalized, long lateBucket, double latePrice) {

        public enum Type { UPDATED, FINALIZED, LATE_EVENT, UNAVAILABLE }

        public static AggregateResult updated() {
            return new AggregateResult(Type.UPDATED, null, 0, 0);
        }

        public static AggregateResult finalized(Candle candle) {
            return new AggregateResult(Type.FINALIZED, candle, 0, 0);
        }

        public static AggregateResult lateEvent(long bucket, double price) {
            return new AggregateResult(Type.LATE_EVENT, null, bucket, price);
        }

        public static AggregateResult unavailable() {
            return new AggregateResult(Type.UNAVAILABLE, null, 0, 0);
        }
    }
}
