package com.app;

// Java built-in HTTP client (available since Java 11)
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

// For exception handling
import java.io.IOException;

// Google Gson for JSON ↔ Java conversion
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * Handles outbound HTTP requests and converts JSON responses into Java objects.
 *
 * <h3>How the JSON conversion works</h3>
 * <ol>
 *   <li>An {@link HttpClient} sends a GET request to the target URL.</li>
 *   <li>The response body is received as a plain {@code String}.</li>
 *   <li>{@link Gson#fromJson(String, Class)} deserializes that string into a
 *       {@link PricePoint} object by matching each JSON key to a field with the
 *       same name in the target class.</li>
 * </ol>
 */
public class ApiFetcher {

    // ── Sample endpoint ─────────────────────────────────────
    // Replace this URL with a real API endpoint in production.
    // The endpoint should return JSON similar to:
    //   { "symbol": "AAPL", "price": 227.34, "currency": "USD", "timestamp": 1726934400 }
    private static final String SAMPLE_ENDPOINT =
            "https://jsonplaceholder.typicode.com/posts/1";

    // HttpClient is thread-safe and reusable across requests.
    private final HttpClient httpClient;

    // A single Gson instance can be reused; it is also thread-safe.
    private final Gson gson;

    public ApiFetcher() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    /**
     * Sends an HTTP GET request to {@code url} and returns the raw JSON
     * response body.
     *
     * @param url the endpoint to query
     * @return the response body as a String, or {@code null} on failure
     */
    public String fetchJson(String url) {
        try {
            // 1. Build an HTTP GET request for the given URL.
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            // 2. Send the request and collect the response body as a String.
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 3. Check for a successful status code (2xx).
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            } else {
                System.err.println("HTTP request failed with status: " + response.statusCode());
                return null;
            }

        } catch (IOException e) {
            System.err.println("Network error during HTTP request: " + e.getMessage());
            e.printStackTrace();
            return null;
        } catch (InterruptedException e) {
            System.err.println("HTTP request was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt(); // Restore the interrupt flag
            return null;
        }
    }

    /**
     * Fetches JSON from the given URL and deserializes it into a
     * {@link PricePoint} object using Gson.
     *
     * <p><b>Gson conversion steps:</b></p>
     * <ol>
     *   <li>Receive the raw JSON string from {@link #fetchJson(String)}.</li>
     *   <li>Call {@code gson.fromJson(json, PricePoint.class)}. Internally,
     *       Gson uses reflection to inspect every field in {@code PricePoint}
     *       and looks for a matching key in the JSON object.</li>
     *   <li>For each match it finds, Gson converts the JSON value to the
     *       field's declared Java type and sets it on a new instance.</li>
     *   <li>Fields with no matching JSON key keep their default values.</li>
     * </ol>
     *
     * @param url the endpoint to query
     * @return a populated {@link PricePoint}, or {@code null} on failure
     */
    public PricePoint fetchPricePoint(String url) {
        // Step 1: Get the raw JSON string from the endpoint.
        String json = fetchJson(url);

        if (json == null) {
            System.err.println("No JSON data received.");
            return null;
        }

        try {
            // Step 2: Deserialize the JSON string into a PricePoint object.
            //
            // gson.fromJson() performs the following under the hood:
            //   a) Parses the JSON string into an internal tree.
            //   b) Creates a new PricePoint instance (via its no-arg constructor).
            //   c) For every JSON key that matches a field name, it converts
            //      the value and assigns it to the field using reflection.
            //
            // Example JSON  →  Java mapping:
            //   "symbol": "AAPL"       →  PricePoint.symbol   = "AAPL"
            //   "price": 227.34        →  PricePoint.price    = 227.34
            //   "currency": "USD"      →  PricePoint.currency = "USD"
            //   "timestamp": 172693..  →  PricePoint.timestamp = 1726934400
            PricePoint pricePoint = gson.fromJson(json, PricePoint.class);

            System.out.println("Successfully parsed JSON into: " + pricePoint);
            return pricePoint;

        } catch (JsonSyntaxException e) {
            // Thrown when the JSON is malformed or cannot be mapped to the
            // target class's field types (e.g., a String where a number is
            // expected).
            System.err.println("Failed to parse JSON: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convenience method that fetches from the built-in sample endpoint.
     *
     * @return a {@link PricePoint} parsed from the sample endpoint
     */
    public PricePoint fetchSamplePricePoint() {
        return fetchPricePoint(SAMPLE_ENDPOINT);
    }
}
