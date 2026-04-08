package com.example.hellospringapi.market;

import java.util.Arrays;

public enum CandleInterval {
    S1("1s", 1),
    S5("5s", 5),
    M1("1m", 60),
    M15("15m", 900),
    H1("1h", 3600);

    private final String value;
    private final long seconds;

    CandleInterval(String value, long seconds) {
        this.value = value;
        this.seconds = seconds;
    }

    public String value() {
        return value;
    }

    public long seconds() {
        return seconds;
    }

    public long bucketStart(long timestampSeconds) {
        return timestampSeconds - (timestampSeconds % seconds);
    }

    public static CandleInterval fromValue(String value) {
        return Arrays.stream(values())
                .filter(interval -> interval.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported interval: " + value));
    }
}
