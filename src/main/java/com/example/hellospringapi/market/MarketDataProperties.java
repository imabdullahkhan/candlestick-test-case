package com.example.hellospringapi.market;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "market")
public class MarketDataProperties {
    private List<String> symbols = List.of("BTC-USD", "ETH-USD");
    private List<String> intervals = List.of("1s", "5s", "1m");
    private boolean simulatorEnabled = true;
    private long simulatorFixedRateMs = 1000L;
    private int simulatorMinTicksPerRun = 1;
    private int simulatorMaxTicksPerRun = 12;

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public List<String> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<String> intervals) {
        this.intervals = intervals;
    }

    public boolean isSimulatorEnabled() {
        return simulatorEnabled;
    }

    public void setSimulatorEnabled(boolean simulatorEnabled) {
        this.simulatorEnabled = simulatorEnabled;
    }

    public long getSimulatorFixedRateMs() {
        return simulatorFixedRateMs;
    }

    public void setSimulatorFixedRateMs(long simulatorFixedRateMs) {
        this.simulatorFixedRateMs = simulatorFixedRateMs;
    }

    public int getSimulatorMinTicksPerRun() {
        return simulatorMinTicksPerRun;
    }

    public void setSimulatorMinTicksPerRun(int simulatorMinTicksPerRun) {
        this.simulatorMinTicksPerRun = simulatorMinTicksPerRun;
    }

    public int getSimulatorMaxTicksPerRun() {
        return simulatorMaxTicksPerRun;
    }

    public void setSimulatorMaxTicksPerRun(int simulatorMaxTicksPerRun) {
        this.simulatorMaxTicksPerRun = simulatorMaxTicksPerRun;
    }
}
