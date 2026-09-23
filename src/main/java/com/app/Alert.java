package com.app;

/**
 * Represents a price alert set by the user for a specific ticker.
 * Stored in the {@code alerts} table in SQLite.
 */
public class Alert {

    private final int id;
    private final String ticker;
    private final double targetPrice;

    public Alert(int id, String ticker, double targetPrice) {
        this.id = id;
        this.ticker = ticker;
        this.targetPrice = targetPrice;
    }

    public int getId()           { return id; }
    public String getTicker()    { return ticker; }
    public double getTargetPrice() { return targetPrice; }

    @Override
    public String toString() {
        return String.format("[%s]  $%.2f", ticker, targetPrice);
    }
}
