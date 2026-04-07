package com.example.hellospringapi.market;

public record BidAskEvent(String symbol, double bid, double ask, long timestamp) {
}
