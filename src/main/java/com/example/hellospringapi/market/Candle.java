package com.example.hellospringapi.market;

public record Candle(long time, double open, double high, double low, double close, long volume) {
}
