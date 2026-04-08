package com.example.hellospringapi.market;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryControllerTest {

    private CandleAggregatorService aggregatorService;
    private HistoryController controller;

    @BeforeEach
    void setUp() {
        aggregatorService = new CandleAggregatorService(null, null, null);
        controller = new HistoryController(aggregatorService);
    }

    @Test
    void validRequestReturnsOkWithCandles() {
        aggregatorService.onEvent(
                new BidAskEvent("BTC-USD", 100.0, 102.0, 1000),
                List.of(CandleInterval.S1)
        );

        HistoryResponse response = controller.history("BTC-USD", "1s", 1000, 1000);

        assertEquals("ok", response.s());
        assertEquals(List.of(1000L), response.t());
        assertEquals(1, response.o().size());
        assertEquals(1, response.h().size());
        assertEquals(1, response.l().size());
        assertEquals(1, response.c().size());
        assertEquals(List.of(1L), response.v());
    }

    @Test
    void noDataReturnsNoDataStatus() {
        HistoryResponse response = controller.history("BTC-USD", "1s", 0, 9999);

        assertEquals("no_data", response.s());
        assertTrue(response.t().isEmpty());
        assertTrue(response.o().isEmpty());
        assertTrue(response.h().isEmpty());
        assertTrue(response.l().isEmpty());
        assertTrue(response.c().isEmpty());
        assertTrue(response.v().isEmpty());
    }

    @Test
    void invalidIntervalThrowsBadRequestException() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> controller.history("BTC-USD", "2s", 0, 100));
        assertEquals("Unsupported interval: 2s", ex.getMessage());
    }

    @Test
    void toBeforeFromThrowsBadRequestException() {
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> controller.history("BTC-USD", "1s", 100, 50));
        assertEquals("'to' must be greater than or equal to 'from'.", ex.getMessage());
    }

    @Test
    void equalFromAndToIsAllowed() {
        HistoryResponse response = controller.history("BTC-USD", "1s", 1000, 1000);
        assertEquals("no_data", response.s());
    }

    @Test
    void responseContainsCorrectOhlcvData() {
        aggregatorService.onEvent(new BidAskEvent("ETH-USD", 50.0, 52.0, 2000), List.of(CandleInterval.S1)); // mid 51
        aggregatorService.onEvent(new BidAskEvent("ETH-USD", 54.0, 56.0, 2000), List.of(CandleInterval.S1)); // mid 55
        aggregatorService.onEvent(new BidAskEvent("ETH-USD", 48.0, 50.0, 2000), List.of(CandleInterval.S1)); // mid 49

        HistoryResponse response = controller.history("ETH-USD", "1s", 2000, 2000);

        assertEquals("ok", response.s());
        assertEquals(51.0, response.o().get(0));
        assertEquals(55.0, response.h().get(0));
        assertEquals(49.0, response.l().get(0));
        assertEquals(49.0, response.c().get(0));
        assertEquals(3L, response.v().get(0));
    }

    @Test
    void multipleBucketsReturnMultipleEntries() {
        aggregatorService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 100), List.of(CandleInterval.S1));
        aggregatorService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 101), List.of(CandleInterval.S1));
        aggregatorService.onEvent(new BidAskEvent("BTC-USD", 10.0, 12.0, 102), List.of(CandleInterval.S1));

        HistoryResponse response = controller.history("BTC-USD", "1s", 100, 102);

        assertEquals("ok", response.s());
        assertEquals(3, response.t().size());
        assertEquals(List.of(100L, 101L, 102L), response.t());
    }

    @Test
    void allIntervalStringsAreAccepted() {
        for (String interval : List.of("1s", "5s", "1m", "15m", "1h")) {
            HistoryResponse response = controller.history("BTC-USD", interval, 0, 100);
            assertEquals("no_data", response.s());
        }
    }

    @Test
    void errorResponseStructureMatchesTradingViewFormat() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        var response = handler.handleBadRequest(new BadRequestException("test error"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("error", response.getBody().s());
        assertEquals("test error", response.getBody().errmsg());
    }
}
