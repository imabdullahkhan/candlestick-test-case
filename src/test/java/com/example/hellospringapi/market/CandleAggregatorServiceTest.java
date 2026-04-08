package com.example.hellospringapi.market;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandleAggregatorServiceTest {

    @Test
    void shouldAggregateSingleBucketOhlc() {
        CandleAggregatorService service = new CandleAggregatorService(null, null);
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        service.onEvent(new BidAskEvent("BTC-USD", 100.0, 102.0, 1000), intervals); // mid 101
        service.onEvent(new BidAskEvent("BTC-USD", 102.0, 104.0, 1000), intervals); // mid 103
        service.onEvent(new BidAskEvent("BTC-USD", 99.0, 101.0, 1000), intervals);  // mid 100

        List<Candle> candles = service.getHistory("BTC-USD", CandleInterval.S1, 1000, 1000);
        assertEquals(1, candles.size());

        Candle candle = candles.get(0);
        assertEquals(1000L, candle.time());
        assertEquals(101.0, candle.open());
        assertEquals(103.0, candle.high());
        assertEquals(100.0, candle.low());
        assertEquals(100.0, candle.close());
        assertEquals(3L, candle.volume());
    }

    @Test
    void shouldClosePreviousBucketWhenNewBucketStarts() {
        CandleAggregatorService service = new CandleAggregatorService(null, null);
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        service.onEvent(new BidAskEvent("ETH-USD", 10.0, 12.0, 2000), intervals); // mid 11
        service.onEvent(new BidAskEvent("ETH-USD", 12.0, 14.0, 2001), intervals); // mid 13, new bucket

        List<Candle> oldBucket = service.getHistory("ETH-USD", CandleInterval.S1, 2000, 2000);
        List<Candle> newBucket = service.getHistory("ETH-USD", CandleInterval.S1, 2001, 2001);

        assertEquals(1, oldBucket.size());
        assertEquals(11.0, oldBucket.get(0).open());
        assertEquals(11.0, oldBucket.get(0).close());
        assertEquals(1L, oldBucket.get(0).volume());

        assertEquals(1, newBucket.size());
        assertEquals(13.0, newBucket.get(0).open());
        assertEquals(13.0, newBucket.get(0).close());
    }
}
