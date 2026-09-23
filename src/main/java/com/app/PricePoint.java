package com.app;

/**
 * A data class representing one price data point.
 *
 * <p>Gson maps JSON keys to fields by name. For example, the JSON key
 * {@code "symbol"} is mapped to the {@code symbol} field automatically.</p>
 *
 * <p>The {@code date} field (format {@code "YYYY-MM-DD"}) is used both
 * for the chart X-axis and for persisting data in SQLite.</p>
 */
public class PricePoint {

    private String symbol;
    private double price;
    private String currency;
    private long   timestamp; // Unix epoch seconds (optional)
    private String date;      // "YYYY-MM-DD" — primary date representation

    /** No-arg constructor required by Gson for reflective instantiation. */
    public PricePoint() {}

    public PricePoint(String symbol, double price, String currency, String date) {
        this.symbol   = symbol;
        this.price    = price;
        this.currency = currency;
        this.date     = date;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getSymbol()    { return symbol; }
    public double getPrice()     { return price; }
    public String getCurrency()  { return currency; }
    public long   getTimestamp() { return timestamp; }
    public String getDate()      { return date; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setSymbol(String symbol)       { this.symbol    = symbol; }
    public void setPrice(double price)         { this.price     = price; }
    public void setCurrency(String currency)   { this.currency  = currency; }
    public void setTimestamp(long timestamp)   { this.timestamp = timestamp; }
    public void setDate(String date)           { this.date      = date; }

    @Override
    public String toString() {
        return "PricePoint{symbol='" + symbol + "', date='" + date
                + "', price=" + price + ", currency='" + currency + "'}";
    }
}
