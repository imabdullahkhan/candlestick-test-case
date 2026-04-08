package com.example.hellospringapi.market.model;

public record BidAskEvent(String symbol, double bid, double ask, long timestamp) {
}
