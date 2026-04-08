package com.example.hellospringapi.market;

import com.example.hellospringapi.market.aggregation.CandleIngestionService;
import com.example.hellospringapi.market.aggregation.CandleQueryService;
import com.example.hellospringapi.market.aggregation.bucket.InMemoryBucketStore;
import com.example.hellospringapi.market.model.BidAskEvent;
import com.example.hellospringapi.market.model.Candle;
import com.example.hellospringapi.market.model.CandleInterval;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandleAggregatorServiceTest {

    private CandleIngestionService ingestionService;
    private CandleQueryService queryService;

    @BeforeEach
    void setUp() {
        ingestionService = new CandleIngestionService(new InMemoryBucketStore(), null);
        queryService = new CandleQueryService(ingestionService, null, null);
    }

    @Test
    void shouldAggregateSingleBucketOhlc() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 100.0, 102.0, 1000), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 102.0, 104.0, 1000), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 99.0, 101.0, 1000), intervals);

        List<Candle> candles = queryService.getHistory("BTC-USD", CandleInterval.S1, 1000, 1000);
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
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("ETH-USD", 10.0, 12.0, 2000), intervals);
        ingestionService.onEvent(new BidAskEvent("ETH-USD", 12.0, 14.0, 2001), intervals);

        List<Candle> oldBucket = queryService.getHistory("ETH-USD", CandleInterval.S1, 2000, 2000);
        List<Candle> newBucket = queryService.getHistory("ETH-USD", CandleInterval.S1, 2001, 2001);

        assertEquals(1, oldBucket.size());
        assertEquals(11.0, oldBucket.get(0).open());
        assertEquals(11.0, oldBucket.get(0).close());
        assertEquals(1L, oldBucket.get(0).volume());

        assertEquals(1, newBucket.size());
        assertEquals(13.0, newBucket.get(0).open());
        assertEquals(13.0, newBucket.get(0).close());
    }

    @Test
    void singleEventProducesValidCandle() {
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 50.0, 52.0, 5000), List.of(CandleInterval.S1));

        List<Candle> candles = queryService.getHistory("BTC-USD", CandleInterval.S1, 5000, 5000);
        assertEquals(1, candles.size());

        Candle c = candles.get(0);
        assertEquals(51.0, c.open());
        assertEquals(51.0, c.high());
        assertEquals(51.0, c.low());
        assertEquals(51.0, c.close());
        assertEquals(1L, c.volume());
    }

    @Test
    void emptyHistoryReturnsEmptyList() {
        List<Candle> candles = queryService.getHistory("BTC-USD", CandleInterval.S1, 0, 9999);
        assertTrue(candles.isEmpty());
    }

    @Test
    void historyRangeReturnsOnlyMatchingBuckets() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 100), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 101), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 102), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 103), intervals);

        List<Candle> subset = queryService.getHistory("BTC-USD", CandleInterval.S1, 101, 102);
        assertEquals(2, subset.size());
        assertEquals(101L, subset.get(0).time());
        assertEquals(102L, subset.get(1).time());
    }

    @Test
    void multipleSymbolsAreTrackedIndependently() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 100.0, 102.0, 1000), intervals);
        ingestionService.onEvent(new BidAskEvent("ETH-USD", 50.0, 52.0, 1000), intervals);

        List<Candle> btc = queryService.getHistory("BTC-USD", CandleInterval.S1, 1000, 1000);
        List<Candle> eth = queryService.getHistory("ETH-USD", CandleInterval.S1, 1000, 1000);

        assertEquals(1, btc.size());
        assertEquals(101.0, btc.get(0).open());

        assertEquals(1, eth.size());
        assertEquals(51.0, eth.get(0).open());
    }

    @Test
    void multipleIntervalsAggregateIndependently() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1, CandleInterval.M1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 100.0, 102.0, 60), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 200.0, 202.0, 61), intervals);

        List<Candle> s1 = queryService.getHistory("BTC-USD", CandleInterval.S1, 60, 61);
        List<Candle> m1 = queryService.getHistory("BTC-USD", CandleInterval.M1, 60, 61);

        assertEquals(2, s1.size(), "S1 should have two separate buckets");
        assertEquals(1, m1.size(), "M1 should merge both into one bucket");
        assertEquals(2L, m1.get(0).volume());
    }

    @Test
    void lateEventUpdatesHistoricalBucket() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 100.0, 102.0, 1000), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 200.0, 202.0, 1001), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 110.0, 112.0, 1000), intervals);

        List<Candle> historical = queryService.getHistory("BTC-USD", CandleInterval.S1, 1000, 1000);
        assertEquals(1, historical.size());

        Candle c = historical.get(0);
        assertEquals(101.0, c.open());
        assertEquals(111.0, c.high());
        assertEquals(101.0, c.low());
        assertEquals(111.0, c.close());
        assertEquals(2L, c.volume());
    }

    @Test
    void historyResultsAreSortedByTime() {
        List<CandleInterval> intervals = List.of(CandleInterval.S1);

        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 1003), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 1001), intervals);
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 1002), intervals);

        List<Candle> candles = queryService.getHistory("BTC-USD", CandleInterval.S1, 1000, 1005);
        assertEquals(3, candles.size());
        assertTrue(candles.get(0).time() <= candles.get(1).time());
        assertTrue(candles.get(1).time() <= candles.get(2).time());
    }

    @Test
    void midPriceIsAverageOfBidAndAsk() {
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 99.0, 101.0, 1000), List.of(CandleInterval.S1));

        Candle c = queryService.getHistory("BTC-USD", CandleInterval.S1, 1000, 1000).get(0);
        assertEquals(100.0, c.open());
    }

    @Test
    void queryForUnknownSymbolReturnsEmpty() {
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 1000), List.of(CandleInterval.S1));
        assertTrue(queryService.getHistory("UNKNOWN", CandleInterval.S1, 0, 9999).isEmpty());
    }

    @Test
    void queryForDifferentIntervalReturnsEmpty() {
        ingestionService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 1000), List.of(CandleInterval.S1));
        assertTrue(queryService.getHistory("BTC-USD", CandleInterval.H1, 0, 9999).isEmpty());
    }
}
