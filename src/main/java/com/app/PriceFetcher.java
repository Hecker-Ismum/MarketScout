package com.app;

import java.util.List;

/**
 * Contract for any service that can fetch historical price data for an asset.
 *
 * <p>Implementing this interface decouples the UI and scheduler from a concrete
 * fetcher implementation, making it easy to swap in mock data or alternative
 * data sources (e.g. a paid premium API) without touching callers.</p>
 *
 * <h3>Design rationale</h3>
 * <ul>
 *   <li>{@link com.app.ApiFetcher} is the production implementation.</li>
 *   <li>{@link com.app.PriceScheduler} accepts a {@code PriceFetcher} parameter,
 *       so it works with any future implementations automatically.</li>
 * </ul>
 */
public interface PriceFetcher {

    /**
     * Fetches the full historical OHLC series for the given asset.
     *
     * @param ticker the asset symbol (e.g. "AAPL", "BTC")
     * @param apiKey API key for stock endpoints; ignored for crypto
     * @return sorted (ascending date) list of {@link PricePoint}, or {@code null} on error
     */
    List<PricePoint> fetchHistory(String ticker, String apiKey);

    /**
     * Returns a human-readable description of the last error, or an empty
     * string if the most recent call succeeded.
     */
    String getLastError();

    /**
     * Returns {@code true} if the given ticker is recognised as a
     * cryptocurrency symbol (and therefore does not need an API key).
     */
    boolean isCrypto(String ticker);
}
