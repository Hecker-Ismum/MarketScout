package com.app;

/**
 * A simple data class representing a single price data point.
 *
 * Gson maps JSON keys to fields by matching their names. For example,
 * the JSON key "symbol" is automatically mapped to the {@code symbol} field.
 * If a JSON key is missing, the corresponding field retains its default value
 * (null for objects, 0 for primitives).
 */
public class PricePoint {

    private String symbol;
    private double price;
    private String currency;
    private long timestamp;

    /** No-arg constructor required by Gson for reflective instantiation. */
    public PricePoint() {
    }

    public PricePoint(String symbol, double price, String currency, long timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.currency = currency;
        this.timestamp = timestamp;
    }

    // ── Getters ──────────────────────────────────────────────

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // ── Setters ──────────────────────────────────────────────

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "PricePoint{"
                + "symbol='" + symbol + '\''
                + ", price=" + price
                + ", currency='" + currency + '\''
                + ", timestamp=" + timestamp
                + '}';
    }
}
