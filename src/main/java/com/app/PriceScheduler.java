package com.app;

// ──────────────────────────────────────────────
// Concurrency — runs periodic work off the UI thread
// ──────────────────────────────────────────────
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// ──────────────────────────────────────────────
// JavaFX thread bridge
// ──────────────────────────────────────────────
import javafx.application.Platform;

// ──────────────────────────────────────────────
// Functional callback for delivering new prices
// ──────────────────────────────────────────────
import java.util.function.Consumer;

/**
 * Schedules a recurring background task that fetches a price every N minutes
 * and safely delivers the result to the JavaFX Application Thread.
 *
 * <h3>Why is {@link Platform#runLater(Runnable)} necessary?</h3>
 *
 * JavaFX enforces a <b>single-threaded rendering rule</b>: only the
 * <em>JavaFX Application Thread</em> is allowed to read or modify the
 * scene graph (labels, charts, text fields, etc.).
 *
 * The {@link ScheduledExecutorService} runs its tasks on a <b>separate
 * background thread</b> from its own pool. If that thread tried to update
 * a Label or add data to a chart directly, JavaFX would throw an
 * {@code IllegalStateException} — or worse, silently corrupt the scene
 * graph and cause visual glitches or crashes.
 *
 * {@code Platform.runLater(Runnable)} solves this by posting the given
 * {@link Runnable} onto the JavaFX Application Thread's event queue.
 * The FX thread picks it up on its next pulse and executes it safely.
 *
 * <pre>
 *   Background thread            FX Application Thread
 *   ─────────────────            ─────────────────────
 *   fetch price                         ...
 *        │
 *        ├─ Platform.runLater(──►  queued ──► update Label
 *        │     () -> label.setText(...)  )
 *        ▼
 *   (continues / sleeps)
 * </pre>
 */
public class PriceScheduler {

    // A single-threaded pool is enough — we only run one periodic task.
    // Using a daemon thread ensures the executor won't prevent the JVM
    // from exiting when the user closes the window.
    private ScheduledExecutorService scheduler;

    /**
     * Starts a repeating task that fetches a new price every
     * {@code intervalMinutes} minutes.
     *
     * <p>The workflow inside each tick:
     * <ol>
     *   <li>Run on a <b>background thread</b> → simulate (or perform)
     *       an expensive network/database call.</li>
     *   <li>Obtain the result as a {@code double}.</li>
     *   <li>Call {@link Platform#runLater(Runnable)} to hand the value
     *       back to the <b>JavaFX Application Thread</b>, where it is
     *       safe to update any UI control.</li>
     * </ol>
     *
     * @param intervalMinutes how often to fetch, in minutes
     * @param uiCallback      a {@link Consumer} that receives the new price
     *                         <b>on the FX thread</b> — safe to update UI here
     */
    public void startPriceFetching(long intervalMinutes, Consumer<Double> uiCallback) {

        // Create a single-thread scheduled executor.
        // The lambda converts threads to daemon threads so they don't
        // block JVM shutdown when the JavaFX window is closed.
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "PriceScheduler-thread");
            t.setDaemon(true); // JVM can exit even if this thread is alive
            return t;
        });

        // Schedule the task: runs once immediately (initialDelay = 0),
        // then repeats every `intervalMinutes` minutes.
        scheduler.scheduleAtFixedRate(() -> {

            // ──────────────────────────────────────────────────────
            // STEP 1 — Background thread work (heavy / blocking)
            // ──────────────────────────────────────────────────────
            // This code runs on the ScheduledExecutorService's pool thread,
            // NOT on the JavaFX Application Thread.
            // It is safe to do network I/O, database queries, or any
            // long-running work here without freezing the UI.

            try {
                System.out.println("[BG] Fetching price on thread: "
                        + Thread.currentThread().getName());

                // --- Simulate a network call that takes 1-2 seconds ---
                // In production, replace this with:
                //   ApiFetcher fetcher = new ApiFetcher();
                //   PricePoint pp = fetcher.fetchPricePoint(apiUrl);
                //   double newPrice = pp.getPrice();
                double newPrice = simulatePriceFetch();

                System.out.println("[BG] Fetched price: " + newPrice);

                // ──────────────────────────────────────────────────
                // STEP 2 — Hand the result to the JavaFX thread
                // ──────────────────────────────────────────────────
                //
                // WHY Platform.runLater() IS NECESSARY:
                //
                //   • JavaFX is NOT thread-safe. Its scene graph (Labels,
                //     Charts, TextFields, etc.) must only be touched from
                //     the JavaFX Application Thread.
                //
                //   • We are currently on a ScheduledExecutorService pool
                //     thread. If we called label.setText(newPrice) here,
                //     JavaFX would throw IllegalStateException or silently
                //     corrupt the UI.
                //
                //   • Platform.runLater() puts the Runnable onto the FX
                //     event queue. The FX thread picks it up on its next
                //     pulse (~60 Hz) and executes it safely.
                //
                //   • The variable `newPrice` is effectively final, so it
                //     can be captured by the lambda without issues.
                //
                Platform.runLater(() -> {
                    // This lambda runs on the JavaFX Application Thread.
                    // It is now safe to update any UI component.
                    uiCallback.accept(newPrice);
                });

            } catch (Exception e) {
                // Catch any exception so the scheduler doesn't silently
                // cancel future executions (a quirk of ScheduledExecutorService).
                System.err.println("[BG] Error during price fetch: " + e.getMessage());
                e.printStackTrace();
            }

        }, 0, intervalMinutes, TimeUnit.MINUTES);

        System.out.println("[PriceScheduler] Started — fetching every "
                + intervalMinutes + " minute(s).");
    }

    /**
     * Gracefully shuts down the scheduler, waiting briefly for any
     * in-flight task to finish.
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
            System.out.println("[PriceScheduler] Stopped.");
        }
    }

    // ──────────────────────────────────────────────────────────
    // Simulated price fetch (replace with real API call)
    // ──────────────────────────────────────────────────────────

    /**
     * Simulates a slow network call that returns a random price.
     * Replace this with a real {@link ApiFetcher} call in production.
     *
     * @return a simulated closing price between 100 and 350
     */
    private double simulatePriceFetch() {
        // Simulate network latency
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Random price between 100.00 and 350.00
        return 100.0 + (Math.random() * 250.0);
    }
}
