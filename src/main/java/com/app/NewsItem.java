package com.app;

/**
 * A single news article returned by the Alpha Vantage NEWS_SENTIMENT endpoint.
 */
public class NewsItem {

    private final String title;
    private final String url;
    private final String source;
    private final String timePublished;       // "20240101T120000"
    private final String sentimentLabel;      // "Bullish" / "Bearish" / "Neutral"
    private final double sentimentScore;      // -1.0 .. +1.0

    public NewsItem(String title, String url, String source,
                    String timePublished, String sentimentLabel, double sentimentScore) {
        this.title          = title;
        this.url            = url;
        this.source         = source;
        this.timePublished  = timePublished;
        this.sentimentLabel = sentimentLabel;
        this.sentimentScore = sentimentScore;
    }

    public String getTitle()          { return title; }
    public String getUrl()            { return url; }
    public String getSource()         { return source; }
    public String getSentimentLabel() { return sentimentLabel; }
    public double getSentimentScore() { return sentimentScore; }

    /**
     * Converts the raw timestamp ("20240101T120000") to a readable "2024-01-01 12:00" string.
     * Returns the raw value unchanged if it cannot be parsed.
     */
    public String getFormattedDate() {
        if (timePublished == null || timePublished.length() < 13) return timePublished;
        try {
            return timePublished.substring(0, 4) + "-"
                 + timePublished.substring(4, 6) + "-"
                 + timePublished.substring(6, 8) + "  "
                 + timePublished.substring(9, 11) + ":"
                 + timePublished.substring(11, 13);
        } catch (Exception e) {
            return timePublished;
        }
    }
}
