package com.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages JDBC connections to a local SQLite database and provides
 * schema initialization utilities.
 */
public class DatabaseManager {

    private static final String DATABASE_URL = "jdbc:sqlite:marketscout.db";

    /**
     * Opens and returns a new connection to the SQLite database.
     *
     * @return a {@link Connection} to marketscout.db
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }

    /**
     * Creates the {@code price_history} and {@code alerts} tables if they do
     * not already exist.
     */
    public void initializeDatabase() {
        String createPriceHistoryTable =
                "CREATE TABLE IF NOT EXISTS price_history ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "asset_ticker TEXT NOT NULL, "
                + "date TEXT NOT NULL, "
                + "closing_price REAL NOT NULL"
                + ");";

        String createAlertsTable =
                "CREATE TABLE IF NOT EXISTS alerts ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "asset_ticker TEXT NOT NULL, "
                + "target_price REAL NOT NULL"
                + ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createPriceHistoryTable);
            stmt.execute(createAlertsTable);
            System.out.println("Database initialized successfully.");

        } catch (SQLException e) {
            System.err.println("Error initializing the database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
