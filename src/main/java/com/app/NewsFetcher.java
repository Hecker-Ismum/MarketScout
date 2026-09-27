package com.app;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches financial news and sentiment data from the
 * <a href="https://www.alphavantage.co/documentation/#news-sentiment">
 * Alpha Vantage NEWS_SENTIMENT endpoint</a>.
 *
 * <p><b>Note:</b> the free Alpha Vantage demo key only returns news for IBM.
 * Users must supply their own free key for other tickers.</p>
 *
 * <p>Example URL:
 * {@code https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=AAPL&limit=20&apikey=KEY}</p>
 */
public class NewsFetcher {

    private static final String URL_TEMPLATE =
            "https://www.alphavantage.co/query"
            + "?function=NEWS_SENTIMENT"
            + "&tickers=%s"
            + "&limit=25"
            + "&apikey=%s";

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson       gson   = new Gson();

    private String lastError = "";

    public String getLastError() { return lastError; }

    /**
     * Fetches up to 25 recent news articles for the given ticker.
     *
     * @param ticker  the stock symbol (e.g. "AAPL"); crypto symbols are not supported
     * @param apiKey  Alpha Vantage API key (null / blank → "demo" key, works for IBM only)
     * @return a list of {@link NewsItem} objects (may be empty on error or no results)
     */
    public List<NewsItem> fetchNews(String ticker, String apiKey) {
        lastError = "";
        String key = (apiKey == null || apiKey.isBlank()) ? "demo" : apiKey.trim();
        String url = String.format(URL_TEMPLATE, ticker.toUpperCase(), key);

        List<NewsItem> result = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                lastError = "HTTP " + response.statusCode();
                return result;
            }

            JsonObject root = gson.fromJson(response.body(), JsonObject.class);

            // API-level errors
            if (root.has("Note")) {
                lastError = "Rate limit: " + root.get("Note").getAsString();
                return result;
            }
            if (root.has("Information")) {
                lastError = root.get("Information").getAsString();
                return result;
            }

            JsonArray feed = root.getAsJsonArray("feed");
            if (feed == null || feed.isEmpty()) {
                lastError = "No news found for " + ticker + " (demo key only works for IBM)";
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

            System.out.println("[News] Fetched " + result.size() + " articles for " + ticker);

        } catch (Exception e) {
            lastError = "Error fetching news: " + e.getMessage();
            System.err.println("[News] " + lastError);
        }

        return result;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private String safeStr(JsonObject obj, String key) {
        return safeStr(obj, key, "");
    }

    private String safeStr(JsonObject obj, String key, String fallback) {
        return (obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsString() : fallback;
    }

    private double safeDbl(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull())
                ? obj.get(key).getAsDouble() : 0.0;
    }
}
