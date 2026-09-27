package com.app;

/**
 * Represents one holding in the user's portfolio.
 * Stored in the {@code portfolio} SQLite table.
 */
public class PortfolioHolding {

    private final int    id;
    private final String ticker;
    private final double quantity;
    private final double avgBuyPrice;

    public PortfolioHolding(int id, String ticker, double quantity, double avgBuyPrice) {
        this.id          = id;
        this.ticker      = ticker;
        this.quantity    = quantity;
        this.avgBuyPrice = avgBuyPrice;
    }

    public int    getId()          { return id; }
    public String getTicker()      { return ticker; }
    public double getQuantity()    { return quantity; }
    public double getAvgBuyPrice() { return avgBuyPrice; }

    /** Total cost paid: quantity × avgBuyPrice. */
    public double getCostBasis()   { return quantity * avgBuyPrice; }
}
