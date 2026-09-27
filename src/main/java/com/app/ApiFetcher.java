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
 * Fetches historical OHLC price data from real market APIs.
 *
 * <h3>Stocks — Alpha Vantage TIME_SERIES_DAILY</h3>
 * Returns the last ~100 trading days. Requires a free API key.
 * The "demo" key only works for ticker IBM.
 *
 * <h3>Crypto — CoinGecko market_chart</h3>
 * Returns 365 days of daily close prices. No API key required.
 * Crypto tickers are auto-detected via a built-in symbol→id map.
 */
public class ApiFetcher {

    // ── Endpoints ─────────────────────────────────────────────────────────────

    private static final String AV_DAILY =
            "https://www.alphavantage.co/query"
            + "?function=TIME_SERIES_DAILY"
            + "&symbol=%s"
            + "&outputsize=compact"
            + "&apikey=%s";

    private static final String CG_MARKET_CHART =
            "https://api.coingecko.com/api/v3/coins/%s/market_chart"
            + "?vs_currency=usd&days=365";

    // ── Crypto symbol → CoinGecko ID map ─────────────────────────────────────

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
        CRYPTO_IDS.put("SHIB",  "shiba-inu");
        CRYPTO_IDS.put("TRX",   "tron");
        CRYPTO_IDS.put("TON",   "the-open-network");
    }

    // ── Infrastructure ────────────────────────────────────────────────────────

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson       gson       = new Gson();

    private String lastError = "";

    public String  getLastError()            { return lastError; }
    public boolean isCrypto(String ticker)   { return CRYPTO_IDS.containsKey(ticker.toUpperCase()); }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Auto-routes to CoinGecko for crypto, Alpha Vantage for everything else.
     *
     * @return sorted (ascending date) list of {@link PricePoint}, or {@code null} on error
     */
    public List<PricePoint> fetchHistory(String ticker, String apiKey) {
        lastError = "";
        String t = ticker.toUpperCase();
        return isCrypto(t) ? fetchCryptoHistory(t) : fetchStockHistory(t, apiKey);
    }

    // ── Alpha Vantage ─────────────────────────────────────────────────────────

    /**
     * Parses full OHLC data from Alpha Vantage TIME_SERIES_DAILY.
     *
     * <pre>
     * "Time Series (Daily)": {
     *   "2024-01-05": {
     *     "1. open":  "181.99",
     *     "2. high":  "182.76",
     *     "3. low":   "180.17",
     *     "4. close": "181.18",
     *     "5. volume":"42835832"
     *   }, ...
     * }
     * </pre>
     */
    private List<PricePoint> fetchStockHistory(String symbol, String apiKey) {
        String key = (apiKey == null || apiKey.isBlank()) ? "demo" : apiKey.trim();
        String url = String.format(AV_DAILY, symbol, key);
        String json = fetchRaw(url);
        if (json == null) return null;

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("Note")) {
                lastError = "Alpha Vantage rate limit: " + root.get("Note").getAsString();
                return null;
            }
            if (root.has("Information")) {
                lastError = root.get("Information").getAsString();
                return null;
            }
            if (root.has("Error Message")) {
                lastError = root.get("Error Message").getAsString();
                return null;
            }

            JsonObject timeSeries = root.getAsJsonObject("Time Series (Daily)");
            if (timeSeries == null) {
                lastError = "No time-series data in Alpha Vantage response.";
                return null;
            }

            List<PricePoint> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : timeSeries.entrySet()) {
                String     date = entry.getKey();
                JsonObject day  = entry.getValue().getAsJsonObject();

                double open  = day.get("1. open").getAsDouble();
                double high  = day.get("2. high").getAsDouble();
                double low   = day.get("3. low").getAsDouble();
                double close = day.get("4. close").getAsDouble();

                result.add(new PricePoint(symbol, open, high, low, close, "USD", date));
            }

            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[API] Alpha Vantage: " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            lastError = "Parse error (Alpha Vantage): " + e.getMessage();
            return null;
        }
    }

    // ── CoinGecko ─────────────────────────────────────────────────────────────

    /**
     * Fetches daily close prices from CoinGecko.
     * OHLC fields (open / high / low) are set to the close value because the
     * {@code market_chart} endpoint only provides close prices.
     */
    private List<PricePoint> fetchCryptoHistory(String symbol) {
        String coinId = CRYPTO_IDS.get(symbol.toUpperCase());
        if (coinId == null) {
            lastError = "Unknown crypto symbol: " + symbol;
            return null;
        }

        String json = fetchRaw(String.format(CG_MARKET_CHART, coinId));
        if (json == null) return null;

        try {
            JsonObject root   = gson.fromJson(json, JsonObject.class);
            JsonArray  prices = root.getAsJsonArray("prices");
            if (prices == null) {
                lastError = "Unexpected CoinGecko response.";
                return null;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            List<PricePoint> result = new ArrayList<>();

            for (JsonElement elem : prices) {
                JsonArray pair    = elem.getAsJsonArray();
                long      epochMs = pair.get(0).getAsLong();
                double    price   = pair.get(1).getAsDouble();
                String    date    = Instant.ofEpochMilli(epochMs)
                                           .atZone(ZoneOffset.UTC)
                                           .toLocalDate()
                                           .format(fmt);
                // For crypto from this endpoint, OHLC = close price
                result.add(new PricePoint(symbol, price, price, price, price, "USD", date));
            }

            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[API] CoinGecko: " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            lastError = "Parse error (CoinGecko): " + e.getMessage();
            return null;
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

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
            }
            lastError = "HTTP " + response.statusCode();
            return null;

        } catch (IOException e) {
            lastError = "Network error: " + e.getMessage();
            return null;
        } catch (InterruptedException e) {
            lastError = "Request interrupted.";
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
