package com.app;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javafx.application.Platform;

/**
 * Schedules a recurring background task that fetches price history every
 * N minutes and safely delivers the result to the JavaFX Application Thread.
 *
 * <h3>Threading model</h3>
 * <pre>
 *   ScheduledExecutorService thread        JavaFX Application Thread
 *   ──────────────────────────────         ─────────────────────────
 *   fetcher.fetchHistory(...)
 *        │
 *        ├─ Platform.runLater(──►  queued ──► uiCallback.accept(data)
 *        │     () -> uiCallback.accept(data) )
 *        ▼
 *   (sleeps until next interval)
 * </pre>
 *
 * <p>Using {@link Platform#runLater(Runnable)} is mandatory because JavaFX
 * enforces a single-threaded scene-graph rule: only the FX Application Thread
 * may modify labels, charts, or any other UI node.</p>
 */
public class PriceScheduler {

    /** Single-thread pool — one periodic task is sufficient. */
    private ScheduledExecutorService scheduler;

    /**
     * Starts a repeating fetch task.
     *
     * @param fetcher         the {@link ApiFetcher} to use for data retrieval
     * @param ticker          the stock or crypto symbol to fetch
     * @param apiKey          Alpha Vantage API key (ignored for crypto)
     * @param intervalMinutes how often to fetch (minimum 1 minute recommended)
     * @param uiCallback      invoked on the <b>JavaFX Application Thread</b> with
     *                        the latest list of {@link PricePoint} objects;
     *                        may receive {@code null} if the fetch fails
     */
    public void startPriceFetching(ApiFetcher fetcher,
                                   String ticker,
                                   String apiKey,
                                   long intervalMinutes,
                                   Consumer<List<PricePoint>> uiCallback) {

        // Daemon threads allow the JVM to exit even if the pool is still active
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PriceScheduler-thread");
            t.setDaemon(true);
            return t;
        });

        // initialDelay = 0 → first run immediately, then every intervalMinutes
        scheduler.scheduleAtFixedRate(() -> {

            // ── Background thread: do the heavy network I/O here ──────────────
            System.out.println("[Scheduler] Fetching " + ticker
                    + " on thread: " + Thread.currentThread().getName());

            List<PricePoint> data;
            try {
                data = fetcher.fetchHistory(ticker, apiKey);
                System.out.println("[Scheduler] Fetch complete: "
                        + (data != null ? data.size() + " points" : "null (error)"));
            } catch (Exception e) {
                // Catch all to prevent ScheduledExecutorService from silently
                // cancelling future executions on uncaught exceptions.
                System.err.println("[Scheduler] Unexpected error: " + e.getMessage());
                e.printStackTrace();
                data = null;
            }

            // ── Hand result to the JavaFX thread ──────────────────────────────
            final List<PricePoint> finalData = data;
            Platform.runLater(() -> uiCallback.accept(finalData));

        }, 0, intervalMinutes, TimeUnit.MINUTES);

        System.out.println("[Scheduler] Started — every " + intervalMinutes + " minute(s) for " + ticker);
    }

    /**
     * Gracefully shuts down the scheduler, waiting up to 5 seconds for the
     * in-flight task to finish before forcing termination.
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("[Scheduler] Stopped.");
        }
    }

    /** @return {@code true} if the scheduler has been started and not yet stopped */
    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}
