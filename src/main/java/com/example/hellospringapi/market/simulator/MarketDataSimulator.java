package com.example.hellospringapi.market.simulator;

import com.example.hellospringapi.market.aggregation.CandleIngestionService;
import com.example.hellospringapi.market.model.BidAskEvent;
import com.example.hellospringapi.market.model.CandleInterval;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MarketDataSimulator {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSimulator.class);

    private final CandleIngestionService ingestionService;
    private final MarketDataProperties properties;
    private final TaskScheduler taskScheduler;
    private final Map<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();
    private volatile List<CandleInterval> intervals;
    private volatile ScheduledFuture<?> scheduledTask;

    public MarketDataSimulator(
            CandleIngestionService ingestionService,
            MarketDataProperties properties,
            TaskScheduler taskScheduler
    ) {
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    void start() {
        this.intervals = properties.getIntervals().stream().map(CandleInterval::fromValue).toList();
        if (!properties.isSimulatorEnabled()) {
            log.info("Market simulator disabled by configuration.");
            return;
        }

        properties.getSymbols().forEach(symbol -> lastPriceBySymbol.put(symbol, seedPrice(symbol)));
        this.scheduledTask = taskScheduler.scheduleAtFixedRate(
                this::emitBatch, Duration.ofMillis(properties.getSimulatorFixedRateMs()));
        log.info("Market simulator started with symbols={} intervals={} fixedRateMs={}",
                properties.getSymbols(), properties.getIntervals(), properties.getSimulatorFixedRateMs());
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down market simulator...");
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        ingestionService.flushActiveCandles();
        log.info("Market simulator stopped.");
    }

    private void emitBatch() {
        try {
            long now = Instant.now().getEpochSecond();
            int minTicks = Math.max(1, properties.getSimulatorMinTicksPerRun());
            int maxTicks = Math.max(minTicks, properties.getSimulatorMaxTicksPerRun());

            for (String symbol : properties.getSymbols()) {
                int ticks = ThreadLocalRandom.current().nextInt(minTicks, maxTicks + 1);
                for (int i = 0; i < ticks; i++) {
                    BidAskEvent event = nextEvent(symbol, now);
                    ingestionService.onEvent(event, intervals);
                }
            }
        } catch (Exception e) {
            log.error("Error in simulator batch: {}", e.getMessage(), e);
        }
    }

    private BidAskEvent nextEvent(String symbol, long timestamp) {
        double mid = lastPriceBySymbol.compute(symbol, (k, previous) -> {
            double base = previous != null ? previous : seedPrice(symbol);
            double movement = ThreadLocalRandom.current().nextDouble(-3.0, 3.0);
            return Math.max(1.0, base + movement);
        });
        double spread = ThreadLocalRandom.current().nextDouble(0.1, 0.7);
        double bid = round(mid - spread / 2);
        double ask = round(mid + spread / 2);
        return new BidAskEvent(symbol, bid, ask, timestamp);
    }

    private double seedPrice(String symbol) {
        return switch (symbol) {
            case "BTC-USD" -> 64000.0;
            case "ETH-USD" -> 3200.0;
            default -> 100.0 + ThreadLocalRandom.current().nextDouble(50.0, 200.0);
        };
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
