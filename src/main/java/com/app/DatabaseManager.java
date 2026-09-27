package com.app;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages JDBC connections to a local SQLite database and provides
 * full CRUD operations for:
 * <ul>
 *   <li>{@code price_history}  — daily OHLC price data</li>
 *   <li>{@code alerts}         — user price alerts</li>
 *   <li>{@code watchlist}      — saved ticker symbols</li>
 *   <li>{@code portfolio}      — user holdings (ticker, qty, avg buy price)</li>
 * </ul>
 *
 * <p>Database file: {@code marketscout.db} in the working directory.</p>
 */
public class DatabaseManager {

    private static final String DATABASE_URL = "jdbc:sqlite:marketscout.db";

    // ── Connection ────────────────────────────────────────────────────────────

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    // ── Schema Initialization ─────────────────────────────────────────────────

    /** Creates all tables if they don't already exist. Idempotent. */
    public void initializeDatabase() {
        String[] ddl = {
            // Price history with OHLC columns
            "CREATE TABLE IF NOT EXISTS price_history ("
            + "id            INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "asset_ticker  TEXT    NOT NULL, "
            + "date          TEXT    NOT NULL, "
            + "open_price    REAL    NOT NULL DEFAULT 0, "
            + "high_price    REAL    NOT NULL DEFAULT 0, "
            + "low_price     REAL    NOT NULL DEFAULT 0, "
            + "closing_price REAL    NOT NULL, "
            + "UNIQUE(asset_ticker, date)"
            + ");",

            // Price alerts
            "CREATE TABLE IF NOT EXISTS alerts ("
            + "id            INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "asset_ticker  TEXT    NOT NULL, "
            + "target_price  REAL    NOT NULL"
            + ");",

            // Watchlist
            "CREATE TABLE IF NOT EXISTS watchlist ("
            + "id     INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "ticker TEXT    NOT NULL UNIQUE"
            + ");",

            // Portfolio holdings
            "CREATE TABLE IF NOT EXISTS portfolio ("
            + "id            INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "ticker        TEXT    NOT NULL UNIQUE, "
            + "quantity      REAL    NOT NULL, "
            + "avg_buy_price REAL    NOT NULL"
            + ");"
        };

        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {
            for (String sql : ddl) stmt.execute(sql);
            System.out.println("[DB] Schema ready.");
        } catch (SQLException e) {
            System.err.println("[DB] Error initializing schema: " + e.getMessage());
        }
    }

    // ── Price History ─────────────────────────────────────────────────────────

    /**
     * Bulk-upserts price points. Uses {@code INSERT OR REPLACE} so re-fetching
     * the same date updates the row instead of creating a duplicate.
     */
    public void savePriceHistory(String ticker, List<PricePoint> points) {
        if (points == null || points.isEmpty()) return;

        String sql = "INSERT OR REPLACE INTO price_history "
                   + "(asset_ticker, date, open_price, high_price, low_price, closing_price) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);
            for (PricePoint p : points) {
                ps.setString(1, ticker.toUpperCase());
                ps.setString(2, p.getDate());
                ps.setDouble(3, p.getOpen());
                ps.setDouble(4, p.getHigh());
                ps.setDouble(5, p.getLow());
                ps.setDouble(6, p.getPrice());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("[DB] Saved " + points.size() + " rows for " + ticker);

        } catch (SQLException e) {
            System.err.println("[DB] Error saving price history: " + e.getMessage());
        }
    }

    /** Returns all stored price points for a ticker, sorted by date ascending. */
    public List<PricePoint> getPriceHistory(String ticker) {
        String sql = "SELECT date, open_price, high_price, low_price, closing_price "
                   + "FROM price_history WHERE asset_ticker = ? ORDER BY date ASC";
        List<PricePoint> result = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticker.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new PricePoint(
                        ticker,
                        rs.getDouble("open_price"),
                        rs.getDouble("high_price"),
                        rs.getDouble("low_price"),
                        rs.getDouble("closing_price"),
                        "USD",
                        rs.getString("date")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error loading price history: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns the most recent stored closing price for the ticker,
     * or {@code -1} if none is found.
     */
    public double getLatestPrice(String ticker) {
        String sql = "SELECT closing_price FROM price_history "
                   + "WHERE asset_ticker = ? ORDER BY date DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticker.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("closing_price");
        } catch (SQLException e) {
            System.err.println("[DB] Error getting latest price: " + e.getMessage());
        }
        return -1;
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    public void addAlert(String ticker, double targetPrice) {
        String sql = "INSERT INTO alerts (asset_ticker, target_price) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticker.toUpperCase());
            ps.setDouble(2, targetPrice);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error adding alert: " + e.getMessage());
        }
    }

    public List<Alert> getAllAlerts() {
        List<Alert> result = new ArrayList<>();
        String sql = "SELECT id, asset_ticker, target_price FROM alerts ORDER BY id ASC";
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

    public void deleteAlert(int id) {
        String sql = "DELETE FROM alerts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error deleting alert: " + e.getMessage());
        }
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────

    /** Adds a ticker to the watchlist. Silently ignores duplicates (UNIQUE constraint). */
    public void addToWatchlist(String ticker) {
        String sql = "INSERT OR IGNORE INTO watchlist (ticker) VALUES (?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticker.toUpperCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error adding to watchlist: " + e.getMessage());
        }
    }

    public void removeFromWatchlist(int id) {
        String sql = "DELETE FROM watchlist WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error removing from watchlist: " + e.getMessage());
        }
    }

    public List<WatchlistItem> getWatchlist() {
        List<WatchlistItem> result = new ArrayList<>();
        String sql = "SELECT id, ticker FROM watchlist ORDER BY ticker ASC";
        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                result.add(new WatchlistItem(rs.getInt("id"), rs.getString("ticker")));
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error loading watchlist: " + e.getMessage());
        }
        return result;
    }

    // ── Portfolio ─────────────────────────────────────────────────────────────

    /**
     * Inserts or replaces a portfolio holding.
     * UNIQUE constraint on {@code ticker} — updating the same ticker replaces the row.
     */
    public void saveHolding(String ticker, double quantity, double avgBuyPrice) {
        String sql = "INSERT OR REPLACE INTO portfolio (ticker, quantity, avg_buy_price) "
                   + "VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ticker.toUpperCase());
            ps.setDouble(2, quantity);
            ps.setDouble(3, avgBuyPrice);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error saving holding: " + e.getMessage());
        }
    }

    public void deleteHolding(int id) {
        String sql = "DELETE FROM portfolio WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] Error deleting holding: " + e.getMessage());
        }
    }

    public List<PortfolioHolding> getAllHoldings() {
        List<PortfolioHolding> result = new ArrayList<>();
        String sql = "SELECT id, ticker, quantity, avg_buy_price FROM portfolio ORDER BY ticker ASC";
        try (Connection conn = getConnection();
             Statement  stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                result.add(new PortfolioHolding(
                        rs.getInt("id"),
                        rs.getString("ticker"),
                        rs.getDouble("quantity"),
                        rs.getDouble("avg_buy_price")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error loading portfolio: " + e.getMessage());
        }
        return result;
    }
}
