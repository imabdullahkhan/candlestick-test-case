package com.example.hellospringapi.market;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HistoryController {

    private final CandleAggregatorService aggregatorService;

    public HistoryController(CandleAggregatorService aggregatorService) {
        this.aggregatorService = aggregatorService;
    }

    @GetMapping("/history")
    public HistoryResponse history(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam long from,
            @RequestParam long to
    ) {
        CandleInterval candleInterval;
        try {
            candleInterval = CandleInterval.fromValue(interval);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage());
        }

        if (to < from) {
            throw new BadRequestException("'to' must be greater than or equal to 'from'.");
        }

        List<Candle> candles = aggregatorService.getHistory(symbol, candleInterval, from, to);
        if (candles.isEmpty()) {
            return new HistoryResponse(
                    "no_data",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        List<Long> t = candles.stream().map(Candle::time).toList();
        List<Double> o = candles.stream().map(Candle::open).toList();
        List<Double> h = candles.stream().map(Candle::high).toList();
        List<Double> l = candles.stream().map(Candle::low).toList();
        List<Double> c = candles.stream().map(Candle::close).toList();
        List<Long> v = candles.stream().map(Candle::volume).toList();
        return new HistoryResponse("ok", t, o, h, l, c, v);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static class BadRequestException extends RuntimeException {
        private BadRequestException(String message) {
            super(message);
        }
    }
}
