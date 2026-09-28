package com.app;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Fetches financial news and sentiment from the Alpha Vantage NEWS_SENTIMENT
 * endpoint.
 *
 * <h3>Class hierarchy</h3>
 * <pre>
 *   AbstractService
 *       └── NewsFetcher
 * </pre>
 *
 * <p>All HTTP I/O and error handling is inherited from {@link AbstractService};
 * this class only contains news-specific parsing logic.</p>
 *
 * <p><b>Note:</b> the free demo key only returns news for IBM.
 * Users must supply their own free key for other tickers.</p>
 */
public class NewsFetcher extends AbstractService {

    private static final String URL_TEMPLATE =
            "https://www.alphavantage.co/query"
            + "?function=NEWS_SENTIMENT"
            + "&tickers=%s"
            + "&limit=25"
            + "&apikey=%s";

    private final Gson gson = new Gson();

    @Override
    protected String getServiceName() { return "NewsFetcher"; }

    /**
     * Fetches up to 25 recent news articles for the given ticker.
     *
     * @param ticker the stock symbol (e.g. "IBM")
     * @param apiKey Alpha Vantage API key; blank → "demo" (IBM only)
     * @return list of {@link NewsItem} objects (may be empty on error)
     */
    public List<NewsItem> fetchNews(String ticker, String apiKey) {
        lastError = "";
        String key = (apiKey == null || apiKey.isBlank()) ? "demo" : apiKey.trim();
        String url = String.format(URL_TEMPLATE, ticker.toUpperCase(), key);

        List<NewsItem> result = new ArrayList<>();
        String json = fetchRaw(url);
        if (json == null) return result;

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("Note")) {
                logError("Rate limit: " + root.get("Note").getAsString());
                return result;
            }
            if (root.has("Information")) {
                logError(root.get("Information").getAsString());
                return result;
            }

            JsonArray feed = root.getAsJsonArray("feed");
            if (feed == null || feed.isEmpty()) {
                logError("No news found for " + ticker + " (demo key only works for IBM)");
                return result;
            }

            for (int i = 0; i < feed.size(); i++) {
                JsonObject item = feed.get(i).getAsJsonObject();
                result.add(new NewsItem(
                        safeStr(item, "title"),
                        safeStr(item, "url"),
                        safeStr(item, "source"),
                        safeStr(item, "time_published"),
                        safeStr(item, "overall_sentiment_label", "Neutral"),
                        safeDbl(item, "overall_sentiment_score")
                ));
            }
            System.out.println("[NewsFetcher] " + result.size() + " articles for " + ticker);

        } catch (Exception e) {
            logError("Parse error: " + e.getMessage());
        }
        return result;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String safeStr(JsonObject o, String k)               { return safeStr(o, k, ""); }
    private String safeStr(JsonObject o, String k, String fb)    { return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : fb; }
    private double safeDbl(JsonObject o, String k)               { return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsDouble() : 0.0; }
}
