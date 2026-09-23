package com.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Fetches historical price data from real market APIs.
 *
 * <h3>Stocks — Alpha Vantage</h3>
 * <p>Uses the {@code TIME_SERIES_DAILY} endpoint (compact = last 100 trading days).
 * Requires a free API key from <a href="https://www.alphavantage.co">alphavantage.co</a>.
 * The built-in {@code "demo"} key only works for the ticker {@code IBM}.</p>
 *
 * <h3>Crypto — CoinGecko</h3>
 * <p>Uses the {@code /coins/{id}/market_chart} endpoint (365 days = daily granularity).
 * No API key required. Crypto tickers are auto-detected via a built-in symbol→id map.</p>
 */
public class ApiFetcher {

    // ── Alpha Vantage ─────────────────────────────────────────────────────────

    private static final String AV_BASE =
            "https://www.alphavantage.co/query"
            + "?function=TIME_SERIES_DAILY"
            + "&symbol=%s"
            + "&outputsize=compact"   // last ~100 trading days
            + "&apikey=%s";

    // ── CoinGecko ─────────────────────────────────────────────────────────────

    private static final String CG_BASE =
            "https://api.coingecko.com/api/v3/coins/%s/market_chart"
            + "?vs_currency=usd&days=365"; // >90 days → daily granularity

    /** Maps common crypto ticker symbols to their CoinGecko coin IDs. */
    private static final Map<String, String> CRYPTO_IDS = new HashMap<>();
    static {
        CRYPTO_IDS.put("BTC",   "bitcoin");
        CRYPTO_IDS.put("ETH",   "ethereum");
        CRYPTO_IDS.put("BNB",   "binancecoin");
        CRYPTO_IDS.put("SOL",   "solana");
        CRYPTO_IDS.put("ADA",   "cardano");
        CRYPTO_IDS.put("DOGE",  "dogecoin");
        CRYPTO_IDS.put("XRP",   "ripple");
        CRYPTO_IDS.put("DOT",   "polkadot");
        CRYPTO_IDS.put("AVAX",  "avalanche-2");
        CRYPTO_IDS.put("MATIC", "matic-network");
        CRYPTO_IDS.put("LTC",   "litecoin");
        CRYPTO_IDS.put("LINK",  "chainlink");
        CRYPTO_IDS.put("UNI",   "uniswap");
        CRYPTO_IDS.put("ATOM",  "cosmos");
        CRYPTO_IDS.put("XLM",   "stellar");
    }

    // ── Shared infrastructure ─────────────────────────────────────────────────

    /** Thread-safe; reuse across requests. */
    private final HttpClient httpClient;
    /** Thread-safe; reuse across parse calls. */
    private final Gson gson;

    /** Stores the last human-readable error message (for display in the UI). */
    private String lastError = "";

    public ApiFetcher() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson       = new Gson();
    }

    /** @return the last error message produced by a fetch operation. */
    public String getLastError() { return lastError; }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the ticker is a recognised crypto symbol.
     * Used by the caller to display "no API key needed" hints.
     */
    public boolean isCrypto(String ticker) {
        return CRYPTO_IDS.containsKey(ticker.toUpperCase());
    }

    /**
     * Auto-routes to the appropriate data source based on the ticker symbol:
     * <ul>
     *   <li>Crypto symbols (BTC, ETH, …) → CoinGecko (no key needed)</li>
     *   <li>Everything else              → Alpha Vantage (key required)</li>
     * </ul>
     *
     * @param ticker the stock or crypto symbol (case-insensitive)
     * @param apiKey Alpha Vantage API key; ignored for crypto tickers
     * @return a list of {@link PricePoint} objects sorted by date ascending,
     *         or {@code null} on failure (check {@link #getLastError()})
     */
    public List<PricePoint> fetchHistory(String ticker, String apiKey) {
        lastError = "";
        String t = ticker.toUpperCase();
        if (isCrypto(t)) {
            return fetchCryptoHistory(t);
        } else {
            return fetchStockHistory(t, apiKey);
        }
    }

    // ── Alpha Vantage (stocks) ────────────────────────────────────────────────

    /**
     * Fetches daily closing prices from Alpha Vantage for the given stock symbol.
     *
     * <p>Alpha Vantage TIME_SERIES_DAILY response structure:</p>
     * <pre>
     * {
     *   "Meta Data": { ... },
     *   "Time Series (Daily)": {
     *     "2024-01-05": { "4. close": "181.18", ... },
     *     "2024-01-04": { "4. close": "182.01", ... },
     *     ...
     *   }
     * }
     * </pre>
     */
    private List<PricePoint> fetchStockHistory(String symbol, String apiKey) {
        String key = (apiKey == null || apiKey.isBlank()) ? "demo" : apiKey.trim();
        String url = String.format(AV_BASE, symbol, key);

        String json = fetchRaw(url);
        if (json == null) return null;

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            // Detect API-level errors returned inside a 200 response
            if (root.has("Note")) {
                lastError = "Alpha Vantage rate limit reached: " + root.get("Note").getAsString();
                System.err.println("[API] " + lastError);
                return null;
            }
            if (root.has("Information")) {
                lastError = root.get("Information").getAsString();
                System.err.println("[API] " + lastError);
                return null;
            }
            if (root.has("Error Message")) {
                lastError = root.get("Error Message").getAsString();
                System.err.println("[API] " + lastError);
                return null;
            }

            JsonObject timeSeries = root.getAsJsonObject("Time Series (Daily)");
            if (timeSeries == null) {
                lastError = "Unexpected response from Alpha Vantage (no time series found).";
                return null;
            }

            // Parse each date entry into a PricePoint
            List<PricePoint> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : timeSeries.entrySet()) {
                String date  = entry.getKey();                              // "2024-01-05"
                double close = entry.getValue().getAsJsonObject()
                                    .get("4. close").getAsDouble();
                result.add(new PricePoint(symbol, close, "USD", date));
            }

            // Alpha Vantage returns newest first; sort ascending for the chart
            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[API] Alpha Vantage: " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            lastError = "Failed to parse Alpha Vantage response: " + e.getMessage();
            System.err.println("[API] " + lastError);
            return null;
        }
    }

    // ── CoinGecko (crypto) ────────────────────────────────────────────────────

    /**
     * Fetches daily closing prices from CoinGecko for the given crypto symbol.
     *
     * <p>CoinGecko market_chart response structure:</p>
     * <pre>
     * {
     *   "prices": [
     *     [1704067200000, 42265.14],  // [unix_ms, price]
     *     ...
     *   ]
     * }
     * </pre>
     */
    private List<PricePoint> fetchCryptoHistory(String symbol) {
        String coinId = CRYPTO_IDS.get(symbol.toUpperCase());
        if (coinId == null) {
            lastError = "Unknown crypto symbol: " + symbol;
            return null;
        }

        String url  = String.format(CG_BASE, coinId);
        String json = fetchRaw(url);
        if (json == null) return null;

        try {
            JsonObject root   = gson.fromJson(json, JsonObject.class);
            JsonArray  prices = root.getAsJsonArray("prices");

            if (prices == null) {
                lastError = "Unexpected response from CoinGecko.";
                return null;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            List<PricePoint> result = new ArrayList<>();

            for (JsonElement elem : prices) {
                JsonArray pair    = elem.getAsJsonArray();
                long      epochMs = pair.get(0).getAsLong();
                double    price   = pair.get(1).getAsDouble();

                // Convert unix milliseconds → "YYYY-MM-DD"
                LocalDate date = Instant.ofEpochMilli(epochMs)
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate();

                result.add(new PricePoint(symbol, price, "USD", date.format(fmt)));
            }

            // CoinGecko returns oldest first; ensure sorted
            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[API] CoinGecko: " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            lastError = "Failed to parse CoinGecko response: " + e.getMessage();
            System.err.println("[API] " + lastError);
            return null;
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    /**
     * Sends an HTTP GET request to {@code url} and returns the raw response body.
     * Sets {@link #lastError} and returns {@code null} on any failure.
     */
    private String fetchRaw(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                lastError = "HTTP " + response.statusCode() + " from " + url;
                System.err.println("[API] " + lastError);
                return null;
            }

        } catch (IOException e) {
            lastError = "Network error: " + e.getMessage();
            System.err.println("[API] " + lastError);
            return null;
        } catch (InterruptedException e) {
            lastError = "Request interrupted.";
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
