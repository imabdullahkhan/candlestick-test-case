package com.example.hellospringapi.market;

import com.example.hellospringapi.market.simulator.MarketDataProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketDataPropertiesTest {

    @Test
    void defaultSymbols() {
        MarketDataProperties props = new MarketDataProperties();
        assertEquals(List.of("BTC-USD", "ETH-USD"), props.getSymbols());
    }

    @Test
    void defaultIntervals() {
        MarketDataProperties props = new MarketDataProperties();
        assertEquals(List.of("1s", "5s", "1m"), props.getIntervals());
    }

    @Test
    void defaultSimulatorEnabled() {
        MarketDataProperties props = new MarketDataProperties();
        assertTrue(props.isSimulatorEnabled());
    }

    @Test
    void defaultSimulatorFixedRateMs() {
        MarketDataProperties props = new MarketDataProperties();
        assertEquals(1000L, props.getSimulatorFixedRateMs());
    }

    @Test
    void defaultTickRange() {
        MarketDataProperties props = new MarketDataProperties();
        assertEquals(1, props.getSimulatorMinTicksPerRun());
        assertEquals(12, props.getSimulatorMaxTicksPerRun());
    }

    @Test
    void setSymbolsOverridesDefault() {
        MarketDataProperties props = new MarketDataProperties();
        props.setSymbols(List.of("SOL-USD"));
        assertEquals(List.of("SOL-USD"), props.getSymbols());
    }

    @Test
    void setIntervalsOverridesDefault() {
        MarketDataProperties props = new MarketDataProperties();
        props.setIntervals(List.of("1h"));
        assertEquals(List.of("1h"), props.getIntervals());
    }

    @Test
    void setSimulatorEnabled() {
        MarketDataProperties props = new MarketDataProperties();
        props.setSimulatorEnabled(false);
        assertEquals(false, props.isSimulatorEnabled());
    }

    @Test
    void setSimulatorFixedRateMs() {
        MarketDataProperties props = new MarketDataProperties();
        props.setSimulatorFixedRateMs(500L);
        assertEquals(500L, props.getSimulatorFixedRateMs());
    }

    @Test
    void setTickRange() {
        MarketDataProperties props = new MarketDataProperties();
        props.setSimulatorMinTicksPerRun(5);
        props.setSimulatorMaxTicksPerRun(20);
        assertEquals(5, props.getSimulatorMinTicksPerRun());
        assertEquals(20, props.getSimulatorMaxTicksPerRun());
    }
}
