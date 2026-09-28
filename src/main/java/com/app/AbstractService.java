package com.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Base class that centralises shared HTTP infrastructure and error-handling
 * for all network-facing services in the application.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Owns the shared {@link HttpClient} instance (thread-safe singleton).</li>
 *   <li>Provides {@link #fetchRaw(String)} — a reusable GET helper that handles
 *       I/O exceptions, HTTP status codes, and thread-interrupt semantics.</li>
 *   <li>Tracks the last error message via {@link #lastError} so callers can
 *       display meaningful feedback without catching exceptions themselves.</li>
 * </ul>
 *
 * <h3>Subclasses</h3>
 * <ul>
 *   <li>{@link ApiFetcher} — fetches OHLC price history (Alpha Vantage / CoinGecko)</li>
 *   <li>{@link NewsFetcher} — fetches news &amp; sentiment (Alpha Vantage NEWS_SENTIMENT)</li>
 * </ul>
 *
 * <p>The abstract method {@link #getServiceName()} forces each subclass to
 * provide a name used in log output, making log lines instantly traceable.</p>
 */
public abstract class AbstractService {

    /**
     * Shared, thread-safe HTTP client.  {@code HttpClient} is designed to be
     * reused — creating one per request is wasteful.
     */
    protected static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    /** Holds the description of the most recent error (empty = no error). */
    protected String lastError = "";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the error message from the most recent network call,
     * or an empty string if the call succeeded.
     */
    public String getLastError() { return lastError; }

    // ── Abstract hook ─────────────────────────────────────────────────────────

    /**
     * Returns a short, human-readable name for this service, used in log output.
     * Example: {@code "Alpha Vantage"}, {@code "CoinGecko"}, {@code "News"}.
     */
    protected abstract String getServiceName();

    // ── Protected utilities ───────────────────────────────────────────────────

    /**
     * Sends a blocking HTTP GET request to {@code url} and returns the response
     * body as a {@code String}, or {@code null} on any error.
     *
     * <p>On error, {@link #lastError} is populated with a short description and
     * the error is logged to {@code System.err}.</p>
     *
     * @param url fully-qualified URL to request
     * @return response body, or {@code null} on failure
     */
    protected String fetchRaw(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }

            logError("HTTP " + response.statusCode() + " from " + url);
            return null;

        } catch (IOException e) {
            logError("Network error: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            logError("Request interrupted.");
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * Sets {@link #lastError} and prints a prefixed message to {@code System.err}.
     *
     * @param msg human-readable error description
     */
    protected void logError(String msg) {
        lastError = msg;
        System.err.println("[" + getServiceName() + "] " + msg);
    }
}
