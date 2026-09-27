package com.app;

/** An entry in the user's persistent watchlist. */
public class WatchlistItem {

    private final int    id;
    private final String ticker;

    public WatchlistItem(int id, String ticker) {
        this.id     = id;
        this.ticker = ticker;
    }

    public int    getId()     { return id; }
    public String getTicker() { return ticker; }

    @Override public String toString() { return ticker; }
}
