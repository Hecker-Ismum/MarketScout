package com.app;

// ── JavaFX core ───────────────────────────────────────────────────────────────
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;

// ── Layout ────────────────────────────────────────────────────────────────────
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;

// ── Controls ──────────────────────────────────────────────────────────────────
import javafx.scene.control.*;

// ── Chart ─────────────────────────────────────────────────────────────────────
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;

// ── Geometry ──────────────────────────────────────────────────────────────────
import javafx.geometry.Insets;
import javafx.geometry.Pos;

// ── Paint ─────────────────────────────────────────────────────────────────────
import javafx.scene.paint.Color;

// ── Java standard ─────────────────────────────────────────────────────────────
import java.util.List;

/**
 * MarketScout — JavaFX desktop app for tracking stock and crypto prices.
 *
 * <h3>Features (all wired up)</h3>
 * <ul>
 *   <li><b>Fetch &amp; Save</b> — real market data (Alpha Vantage / CoinGecko)
 *       persisted to SQLite</li>
 *   <li><b>Auto-Refresh</b> — background scheduler updates the chart on a
 *       configurable interval</li>
 *   <li><b>Price Alerts</b> — set target prices; triggered alerts highlighted
 *       in the status bar</li>
 *   <li><b>Price Change</b> — colour-coded Δ label after each fetch</li>
 * </ul>
 */
public class Main extends Application {

    // ── Services ─────────────────────────────────────────────────────────────

    private final DatabaseManager db             = new DatabaseManager();
    private final ApiFetcher      apiFetcher     = new ApiFetcher();
    private       PriceScheduler  priceScheduler = new PriceScheduler();

    // ── State ─────────────────────────────────────────────────────────────────

    private String currentTicker  = "";
    private double previousPrice  = 0.0;

    // ── UI controls ──────────────────────────────────────────────────────────

    private TextField         tickerField;
    private TextField         apiKeyField;
    private Button            fetchButton;

    private Label             currentPriceLabel;
    private Label             priceChangeLabel;

    private TextField         intervalField;
    private Button            startStopButton;

    private TextField         alertTargetField;
    private Button            addAlertButton;
    private ListView<Alert>   alertsListView;
    private Button            deleteAlertButton;

    private LineChart<String, Number> priceChart;
    private ProgressIndicator         loadingSpinner;
    private Label                     statusLabel;

    // ─────────────────────────────────────────────────────────────────────────
    // Entry point
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) { launch(args); }

    // ─────────────────────────────────────────────────────────────────────────
    // JavaFX lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void start(Stage primaryStage) {

        // Initialise schema before building UI
        db.initializeDatabase();

        // ── Root layout ───────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #14141f;");

        // ── Assemble regions ──────────────────────────────────────────────────
        root.setTop(buildHeader());
        root.setLeft(buildSidebar());
        root.setCenter(buildChartPane());
        root.setBottom(buildStatusBar());

        // ── Scene & stage ─────────────────────────────────────────────────────
        Scene scene = new Scene(root, 1100, 680);
        scene.setFill(Color.web("#14141f"));

        primaryStage.setTitle("MarketScout — Market Data Viewer");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);

        // Cleanup scheduler when window is closed
        primaryStage.setOnCloseRequest(e -> {
            if (priceScheduler.isRunning()) priceScheduler.stop();
        });

        primaryStage.show();

        // Load any alerts already in the DB
        refreshAlertsList();
    }

    /** Called by JavaFX on application shutdown — additional safety stop. */
    @Override
    public void stop() {
        if (priceScheduler.isRunning()) priceScheduler.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI builders
    // ─────────────────────────────────────────────────────────────────────────

    /** Top header bar with gradient background and app title. */
    private HBox buildHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setStyle(
                "-fx-background-color: linear-gradient(to right, #1a1a35, #1e2a4a);"
                + "-fx-border-color: #2a2a55; -fx-border-width: 0 0 1 0;");

        Label appName = new Label("📈  MarketScout");
        appName.setStyle("-fx-text-fill: #e0e0ff; -fx-font-size: 20px; -fx-font-weight: bold;");

        // Spacer
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hint = new Label("Stock & Crypto Price Tracker");
        hint.setStyle("-fx-text-fill: #5a6a8a; -fx-font-size: 12px;");

        header.getChildren().addAll(appName, spacer, hint);
        return header;
    }

    /** Left sidebar containing all input controls. */
    private ScrollPane buildSidebar() {

        VBox sidebar = new VBox(6);
        sidebar.setPadding(new Insets(14, 12, 14, 12));
        sidebar.setPrefWidth(220);
        sidebar.setStyle("-fx-background-color: #1a1a2e;");

        // ── Section: Symbol ───────────────────────────────────────────────────
        sidebar.getChildren().add(sectionHeader("Symbol"));

        tickerField = styledTextField("e.g. AAPL, BTC, IBM");
        tickerField.setOnAction(e -> performFetch()); // Enter key triggers fetch

        sidebar.getChildren().add(tickerField);
        sidebar.getChildren().add(sectionSubLabel("Crypto auto-detected (no key needed)"));

        apiKeyField = styledTextField("Alpha Vantage API key");
        apiKeyField.setTooltip(new Tooltip(
                "Free key at alphavantage.co\n\"demo\" key only works for IBM"));
        sidebar.getChildren().add(apiKeyField);
        sidebar.getChildren().add(sectionSubLabel("Leave blank → uses demo key (IBM only)"));

        fetchButton = accentButton("⬇  Fetch & Save");
        fetchButton.setOnAction(e -> performFetch());
        sidebar.getChildren().add(fetchButton);

        sidebar.getChildren().add(separator());

        // ── Section: Live Price ───────────────────────────────────────────────
        sidebar.getChildren().add(sectionHeader("Live Price"));

        currentPriceLabel = new Label("—");
        currentPriceLabel.setStyle(
                "-fx-text-fill: #7dd3fc; -fx-font-size: 24px; -fx-font-weight: bold;");

        priceChangeLabel = new Label("");
        priceChangeLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11px;");

        sidebar.getChildren().addAll(currentPriceLabel, priceChangeLabel);
        sidebar.getChildren().add(separator());

        // ── Section: Auto-Refresh ─────────────────────────────────────────────
        sidebar.getChildren().add(sectionHeader("Auto-Refresh"));

        HBox intervalRow = new HBox(6);
        intervalRow.setAlignment(Pos.CENTER_LEFT);
        Label everyLabel = new Label("Every");
        everyLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        intervalField = styledTextField("5");
        intervalField.setPrefWidth(50);
        Label minLabel = new Label("min");
        minLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");
        intervalRow.getChildren().addAll(everyLabel, intervalField, minLabel);
        sidebar.getChildren().add(intervalRow);

        startStopButton = new Button("▶  Start Auto-Refresh");
        startStopButton.setMaxWidth(Double.MAX_VALUE);
        startStopButton.setStyle(startStopStyle(false));
        startStopButton.setOnAction(e -> toggleScheduler());
        sidebar.getChildren().add(startStopButton);
        sidebar.getChildren().add(separator());

        // ── Section: Price Alerts ─────────────────────────────────────────────
        sidebar.getChildren().add(sectionHeader("Price Alerts"));
        sidebar.getChildren().add(sectionSubLabel("Alert triggers when price ≥ target"));

        alertTargetField = styledTextField("Target price (e.g. 200.00)");
        addAlertButton   = secondaryButton("＋  Add Alert");
        addAlertButton.setOnAction(e -> addAlert());
        sidebar.getChildren().addAll(alertTargetField, addAlertButton);

        alertsListView = new ListView<>();
        alertsListView.setPrefHeight(130);
        alertsListView.setStyle(
                "-fx-background-color: #12121e; -fx-border-color: #2a2a45;"
                + "-fx-border-radius: 4; -fx-control-inner-background: #12121e;"
                + "-fx-background-radius: 4;");
        alertsListView.setCellFactory(lv -> new AlertCell());
        sidebar.getChildren().add(alertsListView);

        deleteAlertButton = dangerButton("🗑  Delete Selected");
        deleteAlertButton.setOnAction(e -> deleteSelectedAlert());
        sidebar.getChildren().add(deleteAlertButton);

        // ── Wrap in ScrollPane so sidebar doesn't clip on small windows ───────
        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color: #1a1a2e; -fx-background: #1a1a2e;");
        return scroll;
    }

    /** Center pane: line chart + loading spinner overlay. */
    private StackPane buildChartPane() {

        // X-axis: date strings
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        xAxis.setTickLabelRotation(-45);
        xAxis.setStyle("-fx-tick-label-fill: #9090a0; -fx-text-fill: #9090a0;");

        // Y-axis: price numbers
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (USD)");
        yAxis.setStyle("-fx-tick-label-fill: #9090a0; -fx-text-fill: #9090a0;");

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("Select a ticker and click Fetch");
        priceChart.setAnimated(true);
        priceChart.setCreateSymbols(false); // cleaner line without dots for large datasets
        priceChart.setStyle("-fx-background-color: #14141f;");
        priceChart.setLegendVisible(true);

        // Loading spinner (hidden by default)
        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(60, 60);
        loadingSpinner.setStyle("-fx-progress-color: #4a90d9;");
        loadingSpinner.setVisible(false);

        StackPane pane = new StackPane(priceChart, loadingSpinner);
        pane.setStyle("-fx-background-color: #14141f;");
        return pane;
    }

    /** Bottom status bar. */
    private HBox buildStatusBar() {
        statusLabel = new Label("Ready. Enter a ticker symbol and click Fetch & Save.");
        statusLabel.setStyle("-fx-text-fill: #6a7a9a; -fx-font-size: 11px;");

        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(6, 14, 6, 14));
        bar.setStyle("-fx-background-color: #111120; -fx-border-color: #2a2a45;"
                + "-fx-border-width: 1 0 0 0;");
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Feature logic
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches price history for the ticker in the input field, then:
     * saves to DB, updates the chart, updates the live-price label, checks alerts.
     * Runs the network call on a background thread.
     */
    private void performFetch() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) {
            setStatus("⚠  Please enter a ticker symbol.", true);
            return;
        }

        String apiKey = apiKeyField.getText().trim();
        // For crypto tickers the apiKey is irrelevant, so no warning needed
        boolean isCrypto = apiFetcher.isCrypto(ticker);

        fetchButton.setDisable(true);
        loadingSpinner.setVisible(true);
        setStatus("Fetching " + ticker + (isCrypto ? " from CoinGecko…" : " from Alpha Vantage…"), false);

        new Thread(() -> {
            List<PricePoint> data = apiFetcher.fetchHistory(ticker, apiKey);

            Platform.runLater(() -> {
                fetchButton.setDisable(false);
                loadingSpinner.setVisible(false);

                if (data == null || data.isEmpty()) {
                    String err = apiFetcher.getLastError();
                    setStatus("✗  " + (err.isEmpty() ? "No data returned for " + ticker : err), true);
                } else {
                    currentTicker = ticker;
                    db.savePriceHistory(ticker, data);
                    updateChart(ticker, data);
                    updateCurrentPrice(data);
                    checkAlerts(data);
                    setStatus("✓  Loaded " + data.size() + " data points for "
                            + ticker + " and saved to database.", false);
                }
            });
        }).start();
    }

    /** Toggles the PriceScheduler on/off. */
    private void toggleScheduler() {
        if (priceScheduler.isRunning()) {
            priceScheduler.stop();
            // Create a fresh instance so it can be restarted later
            priceScheduler = new PriceScheduler();
            startStopButton.setText("▶  Start Auto-Refresh");
            startStopButton.setStyle(startStopStyle(false));
            setStatus("⏹  Auto-refresh stopped.", false);
            return;
        }

        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) {
            setStatus("⚠  Enter a ticker before starting auto-refresh.", true);
            return;
        }

        long interval = 5;
        try {
            interval = Math.max(1, Long.parseLong(intervalField.getText().trim()));
        } catch (NumberFormatException ignored) {
            intervalField.setText("5");
        }

        String apiKey  = apiKeyField.getText().trim();
        long   finalInterval = interval;

        priceScheduler.startPriceFetching(apiFetcher, ticker, apiKey, finalInterval, data -> {
            // This callback is already on the JavaFX thread (via Platform.runLater)
            if (data != null && !data.isEmpty()) {
                currentTicker = ticker;
                db.savePriceHistory(ticker, data);
                updateChart(ticker, data);
                updateCurrentPrice(data);
                checkAlerts(data);
                setStatus("🔄  Auto-refreshed " + ticker + " — "
                        + data.size() + " points.", false);
            } else {
                setStatus("⚠  Auto-refresh failed: " + apiFetcher.getLastError(), true);
            }
        });

        startStopButton.setText("⏹  Stop Auto-Refresh");
        startStopButton.setStyle(startStopStyle(true));
        setStatus("▶  Auto-refresh started for " + ticker
                + " every " + finalInterval + " minute(s).", false);
    }

    /** Adds a new price alert for the current ticker to the database. */
    private void addAlert() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) {
            setStatus("⚠  Enter a ticker first.", true);
            return;
        }

        String targetText = alertTargetField.getText().trim();
        try {
            double target = Double.parseDouble(targetText);
            if (target <= 0) throw new NumberFormatException("must be positive");
            db.addAlert(ticker, target);
            alertTargetField.clear();
            refreshAlertsList();
            setStatus(String.format("🔔  Alert set: %s @ $%.2f", ticker, target), false);
        } catch (NumberFormatException e) {
            setStatus("⚠  Enter a valid positive number for the target price.", true);
        }
    }

    /** Deletes the currently selected alert from the database and list. */
    private void deleteSelectedAlert() {
        Alert selected = alertsListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("⚠  Select an alert to delete.", true);
            return;
        }
        db.deleteAlert(selected.getId());
        refreshAlertsList();
        setStatus("🗑  Alert deleted.", false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chart & label helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Replaces the chart data with a new series from the given price points. */
    private void updateChart(String ticker, List<PricePoint> data) {
        priceChart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(ticker + " Close");

        // For large datasets thin out to at most 120 points so the chart stays readable
        int step = Math.max(1, data.size() / 120);
        for (int i = 0; i < data.size(); i += step) {
            PricePoint p = data.get(i);
            series.getData().add(new XYChart.Data<>(p.getDate(), p.getPrice()));
        }
        // Always include the last point
        if (!data.isEmpty()) {
            PricePoint last = data.get(data.size() - 1);
            if (series.getData().isEmpty()
                    || !series.getData().get(series.getData().size() - 1)
                              .getXValue().equals(last.getDate())) {
                series.getData().add(new XYChart.Data<>(last.getDate(), last.getPrice()));
            }
        }

        priceChart.getData().add(series);
        priceChart.setTitle(ticker + " — Price History");
    }

    /**
     * Updates the current-price and price-change labels.
     * Colour: green for gain, red for loss relative to the previous fetch.
     */
    private void updateCurrentPrice(List<PricePoint> data) {
        if (data == null || data.isEmpty()) return;

        double latest = data.get(data.size() - 1).getPrice();
        currentPriceLabel.setText(String.format("$%.2f", latest));

        if (previousPrice > 0) {
            double change    = latest - previousPrice;
            double changePct = (change / previousPrice) * 100.0;
            String sign      = change >= 0 ? "+" : "";
            String color     = change >= 0 ? "#4caf50" : "#ff5252";
            priceChangeLabel.setText(
                    String.format("%s$%.2f (%s%.2f%%)", sign, change, sign, changePct));
            priceChangeLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px;");
        } else {
            priceChangeLabel.setText("");
        }

        previousPrice = latest;
    }

    /**
     * Compares the latest price against all stored alerts for the current ticker.
     * Triggered alerts (latest ≥ target) are announced in the status bar.
     */
    private void checkAlerts(List<PricePoint> data) {
        if (data == null || data.isEmpty() || currentTicker.isEmpty()) return;

        double latest = data.get(data.size() - 1).getPrice();
        List<Alert> alerts = db.getAllAlerts();
        StringBuilder sb = new StringBuilder();

        for (Alert alert : alerts) {
            if (!alert.getTicker().equalsIgnoreCase(currentTicker)) continue;
            if (latest >= alert.getTargetPrice()) {
                sb.append(String.format("🔔 ALERT: %s hit $%.2f (target $%.2f)  ",
                        alert.getTicker(), latest, alert.getTargetPrice()));
            }
        }

        if (sb.length() > 0) {
            setStatus(sb.toString().trim(), false);
        }

        refreshAlertsList(); // refresh cell colours
    }

    /** Reloads the alerts ListView from the database. */
    private void refreshAlertsList() {
        List<Alert> alerts = db.getAllAlerts();
        alertsListView.getItems().setAll(alerts);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status bar
    // ─────────────────────────────────────────────────────────────────────────

    private void setStatus(String message, boolean isError) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + (isError ? "#ff7070" : "#6a9fd8")
                + "; -fx-font-size: 11px;");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Style helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Label sectionHeader(String text) {
        Label lbl = new Label(text.toUpperCase());
        lbl.setStyle("-fx-text-fill: #6a9fd8; -fx-font-size: 10px; -fx-font-weight: bold;"
                + "-fx-padding: 6 0 2 0;");
        return lbl;
    }

    private Label sectionSubLabel(String text) {
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setStyle("-fx-text-fill: #555570; -fx-font-size: 9px;");
        return lbl;
    }

    private Separator separator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2a2a45;");
        VBox.setMargin(sep, new Insets(6, 0, 6, 0));
        return sep;
    }

    private TextField styledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setStyle(
                "-fx-background-color: #22223a;"
                + "-fx-text-fill: #e0e0e0;"
                + "-fx-prompt-text-fill: #555570;"
                + "-fx-background-radius: 5;"
                + "-fx-border-color: #2a2a55;"
                + "-fx-border-radius: 5;"
                + "-fx-font-size: 12px;");
        return tf;
    }

    private Button accentButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
                "-fx-background-color: #3a7bd5;"
                + "-fx-text-fill: white;"
                + "-fx-font-weight: bold;"
                + "-fx-background-radius: 5;"
                + "-fx-cursor: hand;"
                + "-fx-font-size: 12px;");
        return btn;
    }

    private Button secondaryButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
                "-fx-background-color: #22223a;"
                + "-fx-text-fill: #c0c0e0;"
                + "-fx-background-radius: 5;"
                + "-fx-cursor: hand;"
                + "-fx-border-color: #3a3a6a;"
                + "-fx-border-radius: 5;"
                + "-fx-font-size: 12px;");
        return btn;
    }

    private Button dangerButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
                "-fx-background-color: #3a1a1a;"
                + "-fx-text-fill: #ff7070;"
                + "-fx-background-radius: 5;"
                + "-fx-cursor: hand;"
                + "-fx-border-color: #5a2a2a;"
                + "-fx-border-radius: 5;"
                + "-fx-font-size: 12px;");
        return btn;
    }

    /** Returns the CSS style for the Start/Stop button based on running state. */
    private String startStopStyle(boolean running) {
        return running
                ? "-fx-background-color: #3a1a1a; -fx-text-fill: #ff7070;"
                  + "-fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"
                  + "-fx-border-color: #5a2a2a; -fx-border-radius: 5; -fx-font-size: 12px;"
                : "-fx-background-color: #1a3a1a; -fx-text-fill: #7fbf7f;"
                  + "-fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"
                  + "-fx-border-color: #2a5a2a; -fx-border-radius: 5; -fx-font-size: 12px;";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Alert list cell
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Custom ListView cell that colour-codes alerts:
     * <ul>
     *   <li>Triggered (latest price ≥ target) → red/orange text</li>
     *   <li>Pending                            → normal light text</li>
     * </ul>
     */
    private class AlertCell extends ListCell<Alert> {
        @Override
        protected void updateItem(Alert alert, boolean empty) {
            super.updateItem(alert, empty);
            if (empty || alert == null) {
                setText(null);
                setStyle("-fx-background-color: #12121e;");
                return;
            }

            setText(alert.toString());

            boolean triggered = previousPrice > 0 && previousPrice >= alert.getTargetPrice()
                    && alert.getTicker().equalsIgnoreCase(currentTicker);

            setStyle(triggered
                    ? "-fx-background-color: #2a1010; -fx-text-fill: #ff9060;"
                      + "-fx-font-size: 11px;"
                    : "-fx-background-color: #12121e; -fx-text-fill: #b0b0d0;"
                      + "-fx-font-size: 11px;");
        }
    }
}
