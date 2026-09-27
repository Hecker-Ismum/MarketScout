package com.app;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Persists user preferences to a {@code settings.properties} file
 * stored in the same directory as the database ({@code marketscout.db}).
 *
 * <p>Currently managed settings:</p>
 * <ul>
 *   <li>{@code alphavantage.apikey} — Alpha Vantage API key</li>
 *   <li>{@code scheduler.interval} — auto-refresh interval in minutes</li>
 * </ul>
 *
 * <p>All methods are safe to call from any thread.</p>
 */
public class SettingsManager {

    private static final Path SETTINGS_FILE = Paths.get("settings.properties");

    // ── Key constants ─────────────────────────────────────────────────────────

    public static final String KEY_API_KEY  = "alphavantage.apikey";
    public static final String KEY_INTERVAL = "scheduler.interval";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static final SettingsManager INSTANCE = new SettingsManager();

    public static SettingsManager getInstance() { return INSTANCE; }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Properties props = new Properties();

    private SettingsManager() { load(); }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the stored value for {@code key}, or {@code defaultValue} if absent.
     */
    public synchronized String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Stores {@code value} under {@code key} and immediately persists to disk.
     * A blank value is stored as an empty string (not deleted).
     */
    public synchronized void set(String key, String value) {
        props.setProperty(key, value == null ? "" : value);
        save();
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.exists(SETTINGS_FILE)) return;
        try (InputStream in = Files.newInputStream(SETTINGS_FILE)) {
            props.load(in);
            System.out.println("[Settings] Loaded from " + SETTINGS_FILE.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("[Settings] Could not load settings: " + e.getMessage());
        }
    }

    private void save() {
        try (OutputStream out = Files.newOutputStream(SETTINGS_FILE)) {
            props.store(out, "MarketScout user settings — do not edit manually while app is running");
            System.out.println("[Settings] Saved.");
        } catch (IOException e) {
            System.err.println("[Settings] Could not save settings: " + e.getMessage());
        }
    }
}
