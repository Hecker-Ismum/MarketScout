package com.app;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fetches historical OHLC price data from real market APIs.
 *
 * <h3>Class hierarchy</h3>
 * <pre>
 *   AbstractService          (shared HTTP, error-handling)
 *       └── ApiFetcher       (implements PriceFetcher)
 * </pre>
 *
 * <h3>Stocks — Alpha Vantage TIME_SERIES_DAILY</h3>
 * Returns the last ~100 trading days with full OHLC data.
 * Requires a free API key (demo key only works for IBM).
 *
 * <h3>Crypto — CoinGecko market_chart</h3>
 * Returns 365 days of daily close prices. No API key required.
 * Crypto tickers are auto-detected via a built-in symbol→id map.
 */
public class ApiFetcher extends AbstractService implements PriceFetcher {

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

    private final Gson gson = new Gson();

    @Override
    protected String getServiceName() { return "ApiFetcher"; }

    // ── PriceFetcher interface ─────────────────────────────────────────────────

    /** {@inheritDoc} */
    @Override
    public boolean isCrypto(String ticker) {
        return CRYPTO_IDS.containsKey(ticker.toUpperCase());
    }

    /**
     * Auto-routes to CoinGecko for crypto, Alpha Vantage for everything else.
     * {@inheritDoc}
     */
    @Override
    public List<PricePoint> fetchHistory(String ticker, String apiKey) {
        lastError = "";
        String t = ticker.toUpperCase();
        return isCrypto(t) ? fetchCryptoHistory(t) : fetchStockHistory(t, apiKey);
    }

    // ── Alpha Vantage ─────────────────────────────────────────────────────────

    private List<PricePoint> fetchStockHistory(String symbol, String apiKey) {
        String key = (apiKey == null || apiKey.isBlank()) ? "demo" : apiKey.trim();
        String json = fetchRaw(String.format(AV_DAILY, symbol, key));
        if (json == null) return null;

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("Note")) {
                logError("Alpha Vantage rate limit: " + root.get("Note").getAsString());
                return null;
            }
            if (root.has("Information")) {
                logError(root.get("Information").getAsString());
                return null;
            }
            if (root.has("Error Message")) {
                logError(root.get("Error Message").getAsString());
                return null;
            }

            JsonObject timeSeries = root.getAsJsonObject("Time Series (Daily)");
            if (timeSeries == null) {
                logError("No time-series data in Alpha Vantage response for " + symbol);
                return null;
            }

            List<PricePoint> result = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : timeSeries.entrySet()) {
                String date = entry.getKey();
                JsonObject day = entry.getValue().getAsJsonObject();
                result.add(new PricePoint(
                        symbol,
                        day.get("1. open").getAsDouble(),
                        day.get("2. high").getAsDouble(),
                        day.get("3. low").getAsDouble(),
                        day.get("4. close").getAsDouble(),
                        "USD", date));
            }

            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[Alpha Vantage] " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            logError("Parse error: " + e.getMessage());
            return null;
        }
    }

    // ── CoinGecko ─────────────────────────────────────────────────────────────

    private List<PricePoint> fetchCryptoHistory(String symbol) {
        String coinId = CRYPTO_IDS.get(symbol.toUpperCase());
        if (coinId == null) { logError("Unknown crypto symbol: " + symbol); return null; }

        String json = fetchRaw(String.format(CG_MARKET_CHART, coinId));
        if (json == null) return null;

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            var prices = root.getAsJsonArray("prices");
            if (prices == null) { logError("Unexpected CoinGecko response."); return null; }

            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            List<PricePoint> result = new ArrayList<>();

            for (var elem : prices) {
                var pair    = elem.getAsJsonArray();
                long epochMs = pair.get(0).getAsLong();
                double price = pair.get(1).getAsDouble();
                String date  = Instant.ofEpochMilli(epochMs)
                        .atZone(ZoneOffset.UTC).toLocalDate().format(fmt);
                result.add(new PricePoint(symbol, price, price, price, price, "USD", date));
            }

            result.sort(Comparator.comparing(PricePoint::getDate));
            System.out.println("[CoinGecko] " + result.size() + " points for " + symbol);
            return result;

        } catch (JsonSyntaxException e) {
            logError("Parse error: " + e.getMessage());
            return null;
        }
    }
}
