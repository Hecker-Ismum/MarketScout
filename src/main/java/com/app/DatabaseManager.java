package com.app;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages JDBC connections to a local SQLite database and provides
 * full CRUD operations for price history and price alerts.
 *
 * <p>Database file: {@code marketscout.db} (created next to the working directory).</p>
 */
public class DatabaseManager {

    private static final String DATABASE_URL = "jdbc:sqlite:marketscout.db";

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Opens and returns a new JDBC connection to the SQLite database.
     *
     * @return a live {@link Connection}
     * @throws SQLException if the connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // ── Schema Initialization ─────────────────────────────────────────────────

    /**
     * Creates the {@code price_history} and {@code alerts} tables if they do
     * not already exist. Safe to call multiple times (idempotent).
     */
    public void initializeDatabase() {
        // UNIQUE constraint on (asset_ticker, date) lets us use INSERT OR REPLACE
        // to upsert data without creating duplicate rows.
        String createPriceHistory =
                "CREATE TABLE IF NOT EXISTS price_history ("
                + "id            INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "asset_ticker  TEXT    NOT NULL, "
                + "date          TEXT    NOT NULL, "
                + "closing_price REAL    NOT NULL, "
                + "UNIQUE(asset_ticker, date)"
                + ");";

        String createAlerts =
                "CREATE TABLE IF NOT EXISTS alerts ("
                + "id            INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "asset_ticker  TEXT    NOT NULL, "
                + "target_price  REAL    NOT NULL"
                + ");";

        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute(createPriceHistory);
            stmt.execute(createAlerts);
            System.out.println("[DB] Schema ready.");
        } catch (SQLException e) {
            System.err.println("[DB] Error initializing schema: " + e.getMessage());
        }
    }

    // ── Price History ─────────────────────────────────────────────────────────

    /**
     * Bulk-inserts (or replaces) a list of price points for the given ticker.
     * Uses a single transaction for performance.
     *
     * @param ticker the asset symbol (e.g. "AAPL", "BTC")
     * @param points the list of price points to persist
     */
    public void savePriceHistory(String ticker, List<PricePoint> points) {
        if (points == null || points.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO price_history "
                   + "(asset_ticker, date, closing_price) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // batch everything into one transaction
            for (PricePoint p : points) {
                ps.setString(1, ticker.toUpperCase());
                ps.setString(2, p.getDate());
                ps.setDouble(3, p.getPrice());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("[DB] Saved " + points.size() + " rows for " + ticker);

        } catch (SQLException e) {
            System.err.println("[DB] Error saving price history: " + e.getMessage());
        }
    }

    /**
     * Retrieves all stored price points for the given ticker, ordered by date ascending.
     *
     * @param ticker the asset symbol
     * @return a list of {@link PricePoint} objects, or an empty list if none found
     */
    public List<PricePoint> getPriceHistory(String ticker) {
        String sql = "SELECT date, closing_price FROM price_history "
                   + "WHERE asset_ticker = ? ORDER BY date ASC";

        List<PricePoint> result = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ticker.toUpperCase());
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                PricePoint p = new PricePoint(
                        ticker,
                        rs.getDouble("closing_price"),
                        "USD",
                        rs.getString("date")
                );
                result.add(p);
            }

        } catch (SQLException e) {
            System.err.println("[DB] Error loading price history: " + e.getMessage());
        }

        return result;
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    /**
     * Inserts a new price alert for the given ticker and target price.
     *
     * @param ticker      the asset symbol
     * @param targetPrice the price threshold that should trigger the alert
     */
    public void addAlert(String ticker, double targetPrice) {
        String sql = "INSERT INTO alerts (asset_ticker, target_price) VALUES (?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, ticker.toUpperCase());
            ps.setDouble(2, targetPrice);
            ps.executeUpdate();
            System.out.println("[DB] Alert added: " + ticker + " @ " + targetPrice);

        } catch (SQLException e) {
            System.err.println("[DB] Error adding alert: " + e.getMessage());
        }
    }

    /**
     * Returns all alerts stored in the database, ordered by insertion time.
     *
     * @return a list of {@link Alert} objects
     */
    public List<Alert> getAllAlerts() {
        String sql = "SELECT id, asset_ticker, target_price FROM alerts ORDER BY id ASC";
        List<Alert> result = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                result.add(new Alert(
                        rs.getInt("id"),
                        rs.getString("asset_ticker"),
                        rs.getDouble("target_price")
                ));
            }

        } catch (SQLException e) {
            System.err.println("[DB] Error loading alerts: " + e.getMessage());
        }

        return result;
    }

    /**
     * Deletes the alert with the given primary key.
     *
     * @param id the {@code id} column value of the alert to delete
     */
    public void deleteAlert(int id) {
        String sql = "DELETE FROM alerts WHERE id = ?";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
            System.out.println("[DB] Alert id=" + id + " deleted.");

        } catch (SQLException e) {
            System.err.println("[DB] Error deleting alert: " + e.getMessage());
        }
    }
}
