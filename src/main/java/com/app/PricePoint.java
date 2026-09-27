package com.app;

/**
 * A data class representing one price data point.
 *
 * <p>Fields {@code open}, {@code high}, {@code low}, and {@code price} (= close)
 * map to the Alpha Vantage daily time-series OHLC values.
 * For CoinGecko crypto data, only {@code price} and {@code date} are populated.</p>
 */
public class PricePoint {

    private String symbol;
    private double price;       // closing price
    private double open;
    private double high;
    private double low;
    private String currency;
    private long   timestamp;   // Unix epoch seconds (optional)
    private String date;        // "YYYY-MM-DD"

    /** No-arg constructor required by Gson for reflective instantiation. */
    public PricePoint() {}

    /** Constructor for crypto / simple close-only price points. */
    public PricePoint(String symbol, double price, String currency, String date) {
        this.symbol   = symbol;
        this.price    = price;
        this.currency = currency;
        this.date     = date;
    }

    /** Full OHLC constructor (used for stock data). */
    public PricePoint(String symbol, double open, double high, double low,
                      double close, String currency, String date) {
        this.symbol   = symbol;
        this.open     = open;
        this.high     = high;
        this.low      = low;
        this.price    = close;
        this.currency = currency;
        this.date     = date;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getSymbol()    { return symbol; }
    public double getPrice()     { return price; }   // = close
    public double getOpen()      { return open; }
    public double getHigh()      { return high; }
    public double getLow()       { return low; }
    public String getCurrency()  { return currency; }
    public long   getTimestamp() { return timestamp; }
    public String getDate()      { return date; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setSymbol(String symbol)      { this.symbol    = symbol; }
    public void setPrice(double price)        { this.price     = price; }
    public void setOpen(double open)          { this.open      = open; }
    public void setHigh(double high)          { this.high      = high; }
    public void setLow(double low)            { this.low       = low; }
    public void setCurrency(String currency)  { this.currency  = currency; }
    public void setTimestamp(long timestamp)  { this.timestamp = timestamp; }
    public void setDate(String date)          { this.date      = date; }

    @Override
    public String toString() {
        return "PricePoint{symbol='" + symbol + "', date='" + date
                + "', open=" + open + ", high=" + high + ", low=" + low
                + ", close=" + price + '}';
    }
}
