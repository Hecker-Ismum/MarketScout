package com.app;

// ── JavaFX core ───────────────────────────────────────────────────────────────
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.scene.Scene;

// ── Layout ────────────────────────────────────────────────────────────────────
import javafx.scene.layout.*;

// ── Controls ──────────────────────────────────────────────────────────────────
import javafx.scene.control.*;


// ── Chart ─────────────────────────────────────────────────────────────────────
import javafx.scene.chart.*;

// ── Geometry / Paint ─────────────────────────────────────────────────────────
import javafx.geometry.*;
import javafx.scene.paint.Color;


// ── Java standard ─────────────────────────────────────────────────────────────
import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * MarketScout — full-featured JavaFX desktop market tracker.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Fetch &amp; save stock/crypto OHLC history (Alpha Vantage / CoinGecko)</li>
 *   <li>Watchlist with one-click load from DB</li>
 *   <li>Animated price chart with timeframe selectors (1W / 1M / 3M / 1Y / All)</li>
 *   <li>SMA-20 and SMA-50 / EMA-20 overlays</li>
 *   <li>CSV export via native save dialog</li>
 *   <li>Auto-refresh scheduler</li>
 *   <li>Price alerts with in-app toast notifications</li>
 *   <li>Portfolio tracker with live P&amp;L</li>
 *   <li>News &amp; sentiment feed (Alpha Vantage)</li>
 * </ul>
 */
public class Main extends Application {

    // ── Services ──────────────────────────────────────────────────────────────
    private final DatabaseManager db          = new DatabaseManager();
    private final ApiFetcher      apiFetcher  = new ApiFetcher();
    private final NewsFetcher     newsFetcher = new NewsFetcher();
    private final SettingsManager settings    = SettingsManager.getInstance();
    private       PriceScheduler  scheduler   = new PriceScheduler();

    // ── State ─────────────────────────────────────────────────────────────────
    private Stage          primaryStage;
    private String         currentTicker      = "";
    private double         previousPrice      = 0.0;
    private List<PricePoint> allData          = new ArrayList<>();
    private List<PricePoint> filteredData     = new ArrayList<>();
    private int            currentDays        = 0;    // 0 = All
    private boolean        showSMA20          = false;
    private boolean        showSMA50          = false;
    private boolean        showEMA20          = false;

    // ── UI – sidebar ──────────────────────────────────────────────────────────
    private TextField          tickerField;
    private TextField          apiKeyField;
    private Button             fetchButton;
    private ListView<WatchlistItem> watchlistView;
    private Label              currentPriceLabel;
    private Label              priceChangeLabel;
    private TextField          intervalField;
    private Button             startStopButton;
    private TextField          alertTargetField;
    private ListView<Alert>    alertsListView;

    // ── UI – chart tab ────────────────────────────────────────────────────────
    private LineChart<String, Number> priceChart;
    private ProgressIndicator         chartSpinner;
    private Button                    btn1W, btn1M, btn3M, btn1Y, btnAll;
    private CheckBox                  sma20Box, sma50Box, ema20Box;

    // ── UI – portfolio tab ────────────────────────────────────────────────────
    private TableView<PortfolioRow>   portfolioTable;
    private ObservableList<PortfolioRow> portfolioRows = FXCollections.observableArrayList();
    private TextField                 portTickerField, portQtyField, portBuyField;
    private Label                     totalValueLabel, totalPnlLabel;

    // ── UI – news tab ─────────────────────────────────────────────────────────
    private ListView<NewsItem> newsListView;
    private ProgressIndicator  newsSpinner;
    private Label              newsStatusLabel;

    // ── Status bar ────────────────────────────────────────────────────────────
    private Label statusLabel;

    // ─────────────────────────────────────────────────────────────────────────
    // JavaFX lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        db.initializeDatabase();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #14141f;");
        root.setTop(buildHeader());
        root.setLeft(buildSidebar());
        root.setCenter(buildCenterTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1200, 720);
        scene.setFill(Color.web("#14141f"));
        stage.setTitle("MarketScout — Market Data Viewer");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(550);
        stage.setOnCloseRequest(e -> { if (scheduler.isRunning()) scheduler.stop(); });
        stage.show();

        // ── Restore saved settings ────────────────────────────────────────────
        String savedKey      = settings.get(SettingsManager.KEY_API_KEY, "");
        String savedInterval = settings.get(SettingsManager.KEY_INTERVAL, "5");
        if (!savedKey.isEmpty())      apiKeyField.setText(savedKey);
        if (!savedInterval.isEmpty()) intervalField.setText(savedInterval);

        // Auto-save whenever either field loses focus
        apiKeyField.focusedProperty().addListener((obs, wasFocused, isNow) -> {
            if (!isNow) settings.set(SettingsManager.KEY_API_KEY, apiKeyField.getText().trim());
        });
        intervalField.focusedProperty().addListener((obs, wasFocused, isNow) -> {
            if (!isNow) settings.set(SettingsManager.KEY_INTERVAL, intervalField.getText().trim());
        });

        refreshWatchlistView();
        refreshAlertsList();
        refreshPortfolioTable();
    }

    @Override
    public void stop() { if (scheduler.isRunning()) scheduler.stop(); }

    // ─────────────────────────────────────────────────────────────────────────
    // UI BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildHeader() {
        HBox hdr = new HBox(12);
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(12, 20, 12, 20));
        hdr.setStyle("-fx-background-color: linear-gradient(to right,#1a1a35,#1e2a4a);"
                + "-fx-border-color:#2a2a55;-fx-border-width:0 0 1 0;");
        Label logo = new Label("📈  MarketScout");
        logo.setStyle("-fx-text-fill:#e0e0ff;-fx-font-size:20px;-fx-font-weight:bold;");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tagline = new Label("Stock & Crypto Price Tracker  |  v2.0");
        tagline.setStyle("-fx-text-fill:#5a6a8a;-fx-font-size:11px;");
        hdr.getChildren().addAll(logo, spacer, tagline);
        return hdr;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private ScrollPane buildSidebar() {
        VBox sb = new VBox(5);
        sb.setPadding(new Insets(12, 10, 12, 10));
        sb.setPrefWidth(210);
        sb.setStyle("-fx-background-color:#1a1a2e;");

        // ── Symbol ───────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("Symbol"));
        tickerField = field("e.g. AAPL, BTC, IBM");
        tickerField.setOnAction(e -> performFetch());
        apiKeyField = field("Alpha Vantage API key");
        apiKeyField.setTooltip(new Tooltip("Free key: alphavantage.co\nDemo key only works for IBM"));
        fetchButton = accentBtn("⬇  Fetch & Save");
        fetchButton.setOnAction(e -> performFetch());
        sb.getChildren().addAll(tickerField, subLabel("Crypto: no key needed"),
                apiKeyField, subLabel("Blank → demo key (IBM only)"), fetchButton);
        sb.getChildren().add(divider());

        // ── Watchlist ─────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("Watchlist"));
        HBox watchRow = new HBox(4);
        TextField watchField = field("Ticker…");
        watchField.setPrefWidth(110);
        Button watchAdd = secondaryBtn("＋");
        watchAdd.setPrefWidth(34);
        watchAdd.setOnAction(e -> {
            String t = watchField.getText().trim().toUpperCase();
            if (!t.isEmpty()) { db.addToWatchlist(t); watchField.clear(); refreshWatchlistView(); }
        });
        watchRow.getChildren().addAll(watchField, watchAdd);

        watchlistView = new ListView<>();
        watchlistView.setPrefHeight(100);
        watchlistView.setStyle(listStyle());
        watchlistView.setCellFactory(lv -> new WatchlistCell());
        watchlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
                if (sel != null) loadFromWatchlist(sel.getTicker());
            }
        });

        Button watchDel = dangerBtn("🗑  Remove");
        watchDel.setOnAction(e -> {
            WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
            if (sel != null) { db.removeFromWatchlist(sel.getId()); refreshWatchlistView(); }
        });
        sb.getChildren().addAll(watchRow, watchlistView, watchDel,
                subLabel("Double-click to load ticker"));
        sb.getChildren().add(divider());

        // ── Live Price ────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("Live Price"));
        currentPriceLabel = new Label("—");
        currentPriceLabel.setStyle("-fx-text-fill:#7dd3fc;-fx-font-size:22px;-fx-font-weight:bold;");
        priceChangeLabel = new Label("");
        priceChangeLabel.setStyle("-fx-text-fill:#aaaaaa;-fx-font-size:10px;");
        sb.getChildren().addAll(currentPriceLabel, priceChangeLabel);
        sb.getChildren().add(divider());

        // ── Auto-Refresh ──────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("Auto-Refresh"));
        HBox intRow = new HBox(4);
        intRow.setAlignment(Pos.CENTER_LEFT);
        intervalField = field("5");
        intervalField.setPrefWidth(45);
        Label minLbl = new Label("min");
        minLbl.setStyle("-fx-text-fill:#8888a0;-fx-font-size:11px;");
        intRow.getChildren().addAll(new Label("Every ") {{
            setStyle("-fx-text-fill:#8888a0;-fx-font-size:11px;"); }}, intervalField, minLbl);
        startStopButton = new Button("▶  Start");
        startStopButton.setMaxWidth(Double.MAX_VALUE);
        startStopButton.setStyle(schedulerStyle(false));
        startStopButton.setOnAction(e -> toggleScheduler());
        sb.getChildren().addAll(intRow, startStopButton);
        sb.getChildren().add(divider());

        // ── Alerts ───────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("Price Alerts"));
        sb.getChildren().add(subLabel("Triggers when price ≥ target"));
        alertTargetField = field("Target price…");
        Button addAlertBtn = secondaryBtn("＋  Add Alert");
        addAlertBtn.setOnAction(e -> addAlert());
        alertsListView = new ListView<>();
        alertsListView.setPrefHeight(120);
        alertsListView.setStyle(listStyle());
        alertsListView.setCellFactory(lv -> new AlertCell());
        Button delAlertBtn = dangerBtn("🗑  Delete");
        delAlertBtn.setOnAction(e -> deleteAlert());
        sb.getChildren().addAll(alertTargetField, addAlertBtn, alertsListView, delAlertBtn);

        ScrollPane scroll = new ScrollPane(sb);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:#1a1a2e;-fx-background:#1a1a2e;");
        return scroll;
    }

    // ── Center TabPane ────────────────────────────────────────────────────────

    private TabPane buildCenterTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:#14141f;");

        Tab chartTab = new Tab("📈  Chart");
        chartTab.setContent(buildChartPane());

        Tab portTab = new Tab("💼  Portfolio");
        portTab.setContent(buildPortfolioPane());

        Tab newsTab = new Tab("📰  News");
        newsTab.setContent(buildNewsPane());

        tabs.getTabs().addAll(chartTab, portTab, newsTab);
        tabs.setStyle(
                "-fx-tab-min-width:100px;"
                + "-fx-background-color:#14141f;");
        return tabs;
    }

    // ── Chart pane ────────────────────────────────────────────────────────────

    private BorderPane buildChartPane() {
        // ── Timeframe toolbar ─────────────────────────────────────────────────
        btn1W  = tfBtn("1W");  btn1W.setOnAction(e -> applyTimeframe(7));
        btn1M  = tfBtn("1M");  btn1M.setOnAction(e -> applyTimeframe(30));
        btn3M  = tfBtn("3M");  btn3M.setOnAction(e -> applyTimeframe(90));
        btn1Y  = tfBtn("1Y");  btn1Y.setOnAction(e -> applyTimeframe(365));
        btnAll = tfBtn("All"); btnAll.setOnAction(e -> applyTimeframe(0));

        sma20Box = new CheckBox("SMA 20");
        sma50Box = new CheckBox("SMA 50");
        ema20Box = new CheckBox("EMA 20");
        styleCheckBox(sma20Box, "#f9a825");
        styleCheckBox(sma50Box, "#e53935");
        styleCheckBox(ema20Box, "#8e24aa");
        sma20Box.setOnAction(e -> { showSMA20 = sma20Box.isSelected(); refreshChart(); });
        sma50Box.setOnAction(e -> { showSMA50 = sma50Box.isSelected(); refreshChart(); });
        ema20Box.setOnAction(e -> { showEMA20 = ema20Box.isSelected(); refreshChart(); });

        Button exportBtn = secondaryBtn("↓  Export CSV");
        exportBtn.setOnAction(e -> CsvExporter.export(currentTicker, filteredData, primaryStage));

        HBox toolbar = new HBox(8,
                btn1W, btn1M, btn3M, btn1Y, btnAll,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                sma20Box, sma50Box, ema20Box,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                exportBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 14, 8, 14));
        toolbar.setStyle("-fx-background-color:#1a1a2e;-fx-border-color:#2a2a45;"
                + "-fx-border-width:0 0 1 0;");

        // ── Chart ─────────────────────────────────────────────────────────────
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");
        xAxis.setTickLabelRotation(-45);
        xAxis.setStyle("-fx-tick-label-fill:#9090a0;");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Price (USD)");
        yAxis.setStyle("-fx-tick-label-fill:#9090a0;");

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("Select a ticker and click Fetch & Save");
        priceChart.setAnimated(false);
        priceChart.setCreateSymbols(false);
        priceChart.setLegendVisible(true);
        priceChart.setStyle("-fx-background-color:#14141f;");
        VBox.setVgrow(priceChart, Priority.ALWAYS);

        chartSpinner = new ProgressIndicator();
        chartSpinner.setMaxSize(56, 56);
        chartSpinner.setVisible(false);
        chartSpinner.setStyle("-fx-progress-color:#4a90d9;");

        StackPane chartArea = new StackPane(priceChart, chartSpinner);
        chartArea.setStyle("-fx-background-color:#14141f;");

        BorderPane bp = new BorderPane();
        bp.setTop(toolbar);
        bp.setCenter(chartArea);
        bp.setStyle("-fx-background-color:#14141f;");
        return bp;
    }

    // ── Portfolio pane ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private BorderPane buildPortfolioPane() {
        // ── Add-holding form ──────────────────────────────────────────────────
        portTickerField = field("Ticker (e.g. AAPL)");
        portQtyField    = field("Quantity");
        portBuyField    = field("Avg Buy Price");
        portTickerField.setPrefWidth(120);
        portQtyField.setPrefWidth(90);
        portBuyField.setPrefWidth(110);

        Button addHoldBtn = accentBtn("＋  Add / Update");
        addHoldBtn.setPrefWidth(130);
        addHoldBtn.setOnAction(e -> addPortfolioHolding());

        Button refreshPricesBtn = secondaryBtn("🔄  Refresh Prices");
        refreshPricesBtn.setOnAction(e -> refreshPortfolioTable());

        Button delHoldBtn = dangerBtn("🗑  Remove");
        delHoldBtn.setOnAction(e -> {
            PortfolioRow sel = portfolioTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
                db.deleteHolding(sel.holding.getId());
                refreshPortfolioTable();
                setStatus("Holding removed.", false);
            }
        });

        HBox formRow = new HBox(8, portTickerField, portQtyField, portBuyField,
                addHoldBtn, refreshPricesBtn, delHoldBtn);
        formRow.setPadding(new Insets(10, 14, 10, 14));
        formRow.setAlignment(Pos.CENTER_LEFT);
        formRow.setStyle("-fx-background-color:#1a1a2e;-fx-border-color:#2a2a45;"
                + "-fx-border-width:0 0 1 0;");

        // ── Table ─────────────────────────────────────────────────────────────
        portfolioTable = new TableView<>(portfolioRows);
        portfolioTable.setStyle("-fx-background-color:#12121e;-fx-border-color:#2a2a45;");
        portfolioTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        portfolioTable.setPlaceholder(new Label("No holdings yet — add one above.") {{
            setStyle("-fx-text-fill:#555570;"); }});

        TableColumn<PortfolioRow, String> colTicker  = col("Ticker",         "ticker");
        TableColumn<PortfolioRow, String> colQty     = col("Qty",            "qty");
        TableColumn<PortfolioRow, String> colAvg     = col("Avg Buy",        "avgBuy");
        TableColumn<PortfolioRow, String> colCurrent = col("Current",        "current");
        TableColumn<PortfolioRow, String> colValue   = col("Value",          "value");
        TableColumn<PortfolioRow, String> colPnl     = col("P&L",            "pnl");
        TableColumn<PortfolioRow, String> colPnlPct  = col("P&L %",         "pnlPct");

        // Colour-code P&L cells
        colPnl.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.startsWith("+")
                        ? "-fx-text-fill:#4caf50; -fx-font-weight:bold;"
                        : "-fx-text-fill:#ff5252; -fx-font-weight:bold;");
            }
        });
        colPnlPct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.startsWith("+")
                        ? "-fx-text-fill:#4caf50;"
                        : "-fx-text-fill:#ff5252;");
            }
        });

        portfolioTable.getColumns().addAll(colTicker, colQty, colAvg, colCurrent, colValue, colPnl, colPnlPct);

        // ── Totals bar ────────────────────────────────────────────────────────
        totalValueLabel = new Label("Total Value: —");
        totalValueLabel.setStyle("-fx-text-fill:#7dd3fc;-fx-font-size:14px;-fx-font-weight:bold;");
        totalPnlLabel   = new Label("Total P&L: —");
        totalPnlLabel.setStyle("-fx-text-fill:#aaaaaa;-fx-font-size:12px;");

        HBox totalsBar = new HBox(30, totalValueLabel, totalPnlLabel);
        totalsBar.setPadding(new Insets(10, 14, 10, 14));
        totalsBar.setStyle("-fx-background-color:#111120;-fx-border-color:#2a2a45;"
                + "-fx-border-width:1 0 0 0;");

        BorderPane bp = new BorderPane();
        bp.setTop(formRow);
        bp.setCenter(portfolioTable);
        bp.setBottom(totalsBar);
        bp.setStyle("-fx-background-color:#12121e;");
        return bp;
    }

    // ── News pane ─────────────────────────────────────────────────────────────

    private BorderPane buildNewsPane() {
        // Toolbar
        TextField newsTickerField = field("Ticker (e.g. IBM)");
        newsTickerField.setPrefWidth(130);
        Button fetchNewsBtn = accentBtn("🔍  Fetch News");
        fetchNewsBtn.setPrefWidth(120);
        newsSpinner = new ProgressIndicator();
        newsSpinner.setMaxSize(24, 24);
        newsSpinner.setVisible(false);
        newsStatusLabel = new Label("Enter a ticker and click Fetch News "
                + "(IBM works with demo key)");
        newsStatusLabel.setStyle("-fx-text-fill:#555570;-fx-font-size:11px;");

        fetchNewsBtn.setOnAction(e -> {
            String t = newsTickerField.getText().trim().toUpperCase();
            if (!t.isEmpty()) fetchNews(t);
        });

        HBox newsToolbar = new HBox(8, newsTickerField, fetchNewsBtn, newsSpinner, newsStatusLabel);
        newsToolbar.setPadding(new Insets(10, 14, 10, 14));
        newsToolbar.setAlignment(Pos.CENTER_LEFT);
        newsToolbar.setStyle("-fx-background-color:#1a1a2e;-fx-border-color:#2a2a45;"
                + "-fx-border-width:0 0 1 0;");

        newsListView = new ListView<>();
        newsListView.setStyle("-fx-background-color:#12121e;-fx-border-color:#2a2a45;");
        newsListView.setCellFactory(lv -> new NewsCell());
        newsListView.setPlaceholder(new Label("No news loaded yet.") {{
            setStyle("-fx-text-fill:#555570;"); }});

        BorderPane bp = new BorderPane();
        bp.setTop(newsToolbar);
        bp.setCenter(newsListView);
        bp.setStyle("-fx-background-color:#12121e;");
        return bp;
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        statusLabel = new Label("Ready — enter a ticker and click Fetch & Save.");
        statusLabel.setStyle("-fx-text-fill:#6a7a9a;-fx-font-size:11px;");
        HBox bar = new HBox(statusLabel);
        bar.setPadding(new Insets(5, 14, 5, 14));
        bar.setStyle("-fx-background-color:#111120;-fx-border-color:#2a2a45;"
                + "-fx-border-width:1 0 0 0;");
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATURE LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    // ── Fetch & save ──────────────────────────────────────────────────────────

    private void performFetch() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("⚠  Enter a ticker.", true); return; }
        boolean crypto = apiFetcher.isCrypto(ticker);
        String  src    = crypto ? "CoinGecko" : "Alpha Vantage";

        fetchButton.setDisable(true);
        chartSpinner.setVisible(true);
        setStatus("Fetching " + ticker + " from " + src + "…", false);

        // Persist the key so it survives restarts
        String apiKey = apiKeyField.getText().trim();
        settings.set(SettingsManager.KEY_API_KEY, apiKey);

        new Thread(() -> {
            List<PricePoint> data = apiFetcher.fetchHistory(ticker, apiKey);
            Platform.runLater(() -> {
                fetchButton.setDisable(false);
                chartSpinner.setVisible(false);
                if (data == null || data.isEmpty()) {
                    setStatus("✗  " + (apiFetcher.getLastError().isEmpty()
                            ? "No data for " + ticker : apiFetcher.getLastError()), true);
                } else {
                    onDataReceived(ticker, data);
                    setStatus("✓  " + data.size() + " points for " + ticker
                            + " saved to database.", false);
                }
            });
        }).start();
    }

    /** Called (on FX thread) whenever fresh data arrives — fetch or scheduler. */
    private void onDataReceived(String ticker, List<PricePoint> data) {
        currentTicker = ticker;
        allData       = data;
        db.savePriceHistory(ticker, data);
        applyTimeframe(currentDays);     // also calls refreshChart + updateCurrentPrice
        checkAlerts(data);
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────

    private void loadFromWatchlist(String ticker) {
        tickerField.setText(ticker);
        // Try DB first (instant), then live fetch
        List<PricePoint> cached = db.getPriceHistory(ticker);
        if (!cached.isEmpty()) {
            allData = cached;
            currentTicker = ticker;
            applyTimeframe(currentDays);
            updateCurrentPrice(allData);
            setStatus("✓  Loaded " + cached.size() + " cached points for " + ticker
                    + " (click Fetch & Save to update).", false);
        } else {
            performFetch();
        }
    }

    // ── Timeframe filter ──────────────────────────────────────────────────────

    private void applyTimeframe(int days) {
        currentDays    = days;
        filteredData   = ChartDataHelper.filterByDays(allData, days);
        refreshChart();
        updateCurrentPrice(filteredData);
        // Highlight active button
        List.of(btn1W, btn1M, btn3M, btn1Y, btnAll).forEach(b ->
                b.setStyle(tfBtnStyle(false)));
        Button active = switch (days) {
            case 7   -> btn1W;
            case 30  -> btn1M;
            case 90  -> btn3M;
            case 365 -> btn1Y;
            default  -> btnAll;
        };
        if (active != null) active.setStyle(tfBtnStyle(true));
    }

    // ── Chart refresh ─────────────────────────────────────────────────────────

    private void refreshChart() {
        if (filteredData == null || filteredData.isEmpty()) return;
        priceChart.getData().clear();

        // Main price series
        XYChart.Series<String, Number> price = new XYChart.Series<>();
        price.setName(currentTicker + " Close");
        int step = Math.max(1, filteredData.size() / 150);
        for (int i = 0; i < filteredData.size(); i += step) {
            PricePoint p = filteredData.get(i);
            price.getData().add(new XYChart.Data<>(p.getDate(), p.getPrice()));
        }
        // Ensure last point is always included
        if (!filteredData.isEmpty()) {
            PricePoint last = filteredData.get(filteredData.size() - 1);
            if (price.getData().isEmpty() || !price.getData()
                    .get(price.getData().size() - 1).getXValue().equals(last.getDate())) {
                price.getData().add(new XYChart.Data<>(last.getDate(), last.getPrice()));
            }
        }
        priceChart.getData().add(price);

        // Moving average overlays
        if (showSMA20) priceChart.getData().add(
                ChartDataHelper.computeSMA(filteredData, 20, "SMA 20"));
        if (showSMA50) priceChart.getData().add(
                ChartDataHelper.computeSMA(filteredData, 50, "SMA 50"));
        if (showEMA20) priceChart.getData().add(
                ChartDataHelper.computeEMA(filteredData, 20, "EMA 20"));

        priceChart.setTitle(currentTicker + " — Price History");
    }

    // ── Live price label ──────────────────────────────────────────────────────

    private void updateCurrentPrice(List<PricePoint> data) {
        if (data == null || data.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        currentPriceLabel.setText(String.format("$%.2f", latest));

        if (previousPrice > 0) {
            double delta   = latest - previousPrice;
            double pct     = (delta / previousPrice) * 100.0;
            String sign    = delta >= 0 ? "+" : "";
            String color   = delta >= 0 ? "#4caf50" : "#ff5252";
            priceChangeLabel.setText(
                    String.format("%s$%.2f  (%s%.2f%%)", sign, delta, sign, pct));
            priceChangeLabel.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;");
        }
        previousPrice = latest;
    }

    // ── Alerts ────────────────────────────────────────────────────────────────

    private void addAlert() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("⚠  Enter a ticker first.", true); return; }
        try {
            double target = Double.parseDouble(alertTargetField.getText().trim());
            if (target <= 0) throw new NumberFormatException();
            db.addAlert(ticker, target);
            alertTargetField.clear();
            refreshAlertsList();
            setStatus(String.format("🔔  Alert set: %s @ $%.2f", ticker, target), false);
        } catch (NumberFormatException ex) {
            setStatus("⚠  Enter a valid positive number for the target price.", true);
        }
    }

    private void deleteAlert() {
        Alert sel = alertsListView.getSelectionModel().getSelectedItem();
        if (sel == null) { setStatus("⚠  Select an alert to delete.", true); return; }
        db.deleteAlert(sel.getId());
        refreshAlertsList();
        setStatus("🗑  Alert deleted.", false);
    }

    private void checkAlerts(List<PricePoint> data) {
        if (data == null || data.isEmpty() || currentTicker.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        List<Alert> alerts = db.getAllAlerts();
        for (Alert a : alerts) {
            if (!a.getTicker().equalsIgnoreCase(currentTicker)) continue;
            if (latest >= a.getTargetPrice()) {
                String msg = String.format("%s hit $%.2f (target $%.2f)",
                        currentTicker, latest, a.getTargetPrice());
                setStatus("🔔 ALERT: " + msg, false);
                NotificationService.show("Price Alert Triggered", msg);
            }
        }
        refreshAlertsList();
    }

    // ── Scheduler ─────────────────────────────────────────────────────────────

    private void toggleScheduler() {
        if (scheduler.isRunning()) {
            scheduler.stop();
            scheduler = new PriceScheduler();
            startStopButton.setText("▶  Start");
            startStopButton.setStyle(schedulerStyle(false));
            setStatus("⏹  Auto-refresh stopped.", false);
            return;
        }
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("⚠  Enter a ticker first.", true); return; }
        long interval = 5;
        try { interval = Math.max(1, Long.parseLong(intervalField.getText().trim())); }
        catch (NumberFormatException ignored) { intervalField.setText("5"); }

        long   fin = interval;
        String key = apiKeyField.getText().trim();
        scheduler.startPriceFetching(apiFetcher, ticker, key, fin, data -> {
            if (data != null && !data.isEmpty()) {
                onDataReceived(ticker, data);
                setStatus("🔄  Auto-refreshed " + ticker + " — " + data.size() + " points.", false);
            } else {
                setStatus("⚠  Auto-refresh failed: " + apiFetcher.getLastError(), true);
            }
        });
        startStopButton.setText("⏹  Stop");
        startStopButton.setStyle(schedulerStyle(true));
        setStatus("▶  Auto-refresh started: " + ticker + " every " + fin + " min.", false);
    }

    // ── Portfolio ─────────────────────────────────────────────────────────────

    private void addPortfolioHolding() {
        String ticker = portTickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("⚠  Enter a ticker for the holding.", true); return; }
        try {
            double qty = Double.parseDouble(portQtyField.getText().trim());
            double avg = Double.parseDouble(portBuyField.getText().trim());
            if (qty <= 0 || avg <= 0) throw new NumberFormatException();
            db.saveHolding(ticker, qty, avg);
            portTickerField.clear(); portQtyField.clear(); portBuyField.clear();
            refreshPortfolioTable();
            setStatus(String.format("💼  Holding saved: %.4f %s @ $%.2f", qty, ticker, avg), false);
        } catch (NumberFormatException ex) {
            setStatus("⚠  Enter valid positive numbers for quantity and price.", true);
        }
    }

    private void refreshPortfolioTable() {
        List<PortfolioHolding> holdings = db.getAllHoldings();
        portfolioRows.clear();
        double totalValue = 0, totalCost = 0;
        for (PortfolioHolding h : holdings) {
            double current = db.getLatestPrice(h.getTicker());
            PortfolioRow row = new PortfolioRow(h, current);
            portfolioRows.add(row);
            if (current > 0) totalValue += row.currentValue;
            totalCost += h.getCostBasis();
        }
        double totalPnl    = totalValue - totalCost;
        double totalPnlPct = totalCost > 0 ? (totalPnl / totalCost) * 100 : 0;
        String sign        = totalPnl >= 0 ? "+" : "";
        String pnlColor    = totalPnl >= 0 ? "#4caf50" : "#ff5252";

        totalValueLabel.setText(String.format("Total Value: $%,.2f", totalValue));
        totalPnlLabel.setText(String.format("Total P&L: %s$%,.2f (%s%.2f%%)",
                sign, totalPnl, sign, totalPnlPct));
        totalPnlLabel.setStyle("-fx-text-fill:" + pnlColor + ";-fx-font-size:12px;");
    }

    // ── News ──────────────────────────────────────────────────────────────────

    private void fetchNews(String ticker) {
        newsSpinner.setVisible(true);
        newsListView.getItems().clear();
        newsStatusLabel.setText("Fetching news for " + ticker + "…");
        String key = apiKeyField.getText().trim();
        new Thread(() -> {
            List<NewsItem> items = newsFetcher.fetchNews(ticker, key);
            Platform.runLater(() -> {
                newsSpinner.setVisible(false);
                if (items.isEmpty()) {
                    newsStatusLabel.setText("⚠  " + newsFetcher.getLastError());
                } else {
                    newsListView.getItems().setAll(items);
                    newsStatusLabel.setText("✓  " + items.size() + " articles for " + ticker);
                }
            });
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REFRESH HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void refreshWatchlistView() {
        watchlistView.getItems().setAll(db.getWatchlist());
    }

    private void refreshAlertsList() {
        alertsListView.getItems().setAll(db.getAllAlerts());
    }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (error ? "#ff7070" : "#6a9fd8")
                + ";-fx-font-size:11px;");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STYLE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Label sectionLabel(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-text-fill:#6a9fd8;-fx-font-size:9px;-fx-font-weight:bold;"
                + "-fx-padding:6 0 2 0;");
        return l;
    }

    private Label subLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill:#444460;-fx-font-size:8px;");
        return l;
    }

    private Separator divider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:#2a2a45;");
        VBox.setMargin(s, new Insets(5, 0, 5, 0));
        return s;
    }

    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setStyle("-fx-background-color:#22223a;-fx-text-fill:#e0e0e0;"
                + "-fx-prompt-text-fill:#444460;-fx-background-radius:5;"
                + "-fx-border-color:#2a2a55;-fx-border-radius:5;-fx-font-size:11px;");
        return tf;
    }

    private Button accentBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:#3a7bd5;-fx-text-fill:white;"
                + "-fx-font-weight:bold;-fx-background-radius:5;-fx-font-size:11px;");
        return b;
    }

    private Button secondaryBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:#22223a;-fx-text-fill:#c0c0e0;"
                + "-fx-background-radius:5;-fx-border-color:#3a3a6a;"
                + "-fx-border-radius:5;-fx-font-size:11px;");
        return b;
    }

    private Button dangerBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:#3a1a1a;-fx-text-fill:#ff7070;"
                + "-fx-background-radius:5;-fx-border-color:#5a2a2a;"
                + "-fx-border-radius:5;-fx-font-size:11px;");
        return b;
    }

    private Button tfBtn(String text) {
        Button b = new Button(text);
        b.setStyle(tfBtnStyle(false));
        return b;
    }

    private String tfBtnStyle(boolean active) {
        return active
                ? "-fx-background-color:#3a7bd5;-fx-text-fill:white;"
                  + "-fx-font-weight:bold;-fx-background-radius:4;-fx-font-size:11px;"
                : "-fx-background-color:#22223a;-fx-text-fill:#8888a0;"
                  + "-fx-background-radius:4;-fx-border-color:#2a2a55;"
                  + "-fx-border-radius:4;-fx-font-size:11px;";
    }

    private String schedulerStyle(boolean running) {
        return running
                ? "-fx-background-color:#3a1a1a;-fx-text-fill:#ff7070;"
                  + "-fx-font-weight:bold;-fx-background-radius:5;-fx-font-size:11px;"
                  + "-fx-border-color:#5a2a2a;-fx-border-radius:5;"
                : "-fx-background-color:#1a3a1a;-fx-text-fill:#7fbf7f;"
                  + "-fx-font-weight:bold;-fx-background-radius:5;-fx-font-size:11px;"
                  + "-fx-border-color:#2a5a2a;-fx-border-radius:5;";
    }

    private String listStyle() {
        return "-fx-background-color:#12121e;-fx-border-color:#2a2a45;"
                + "-fx-border-radius:4;-fx-control-inner-background:#12121e;"
                + "-fx-background-radius:4;";
    }

    private void styleCheckBox(CheckBox cb, String accentColor) {
        cb.setStyle("-fx-text-fill:" + accentColor + ";-fx-font-size:11px;");
    }

    /** Create a SimpleStringProperty column backed by a named getter on PortfolioRow. */
    private TableColumn<PortfolioRow, String> col(String header, String field) {
        TableColumn<PortfolioRow, String> col = new TableColumn<>(header);
        col.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().get(field)));
        col.setStyle("-fx-alignment:CENTER;");
        return col;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER: PortfolioRow (pre-computed for TableView)
    // ─────────────────────────────────────────────────────────────────────────

    static class PortfolioRow {
        final PortfolioHolding holding;
        final double currentPrice;
        final double currentValue;
        final double pnl;
        final double pnlPct;

        PortfolioRow(PortfolioHolding h, double currentPrice) {
            this.holding      = h;
            this.currentPrice = currentPrice;
            this.currentValue = currentPrice > 0 ? h.getQuantity() * currentPrice : 0;
            this.pnl          = currentValue > 0 ? currentValue - h.getCostBasis() : 0;
            this.pnlPct       = h.getCostBasis() > 0 ? (pnl / h.getCostBasis()) * 100 : 0;
        }

        /** Dispatch accessor used by {@link TableColumn} lambdas. */
        String get(String field) {
            String sign = pnl >= 0 ? "+" : "";
            return switch (field) {
                case "ticker"  -> holding.getTicker();
                case "qty"     -> String.format("%.4f", holding.getQuantity());
                case "avgBuy"  -> String.format("$%.2f", holding.getAvgBuyPrice());
                case "current" -> currentPrice > 0
                        ? String.format("$%.2f", currentPrice) : "N/A";
                case "value"   -> currentValue > 0
                        ? String.format("$%,.2f", currentValue) : "N/A";
                case "pnl"     -> currentValue > 0
                        ? String.format("%s$%,.2f", sign, pnl) : "N/A";
                case "pnlPct"  -> currentValue > 0
                        ? String.format("%s%.2f%%", sign, pnlPct) : "N/A";
                default        -> "";
            };
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER: Custom ListView cells
    // ─────────────────────────────────────────────────────────────────────────

    private class AlertCell extends ListCell<Alert> {
        @Override protected void updateItem(Alert a, boolean empty) {
            super.updateItem(a, empty);
            if (empty || a == null) { setText(null); setStyle("-fx-background-color:#12121e;"); return; }
            setText(a.toString());
            boolean triggered = previousPrice > 0 && previousPrice >= a.getTargetPrice()
                    && a.getTicker().equalsIgnoreCase(currentTicker);
            setStyle(triggered
                    ? "-fx-background-color:#2a1010;-fx-text-fill:#ff9060;-fx-font-size:11px;"
                    : "-fx-background-color:#12121e;-fx-text-fill:#b0b0d0;-fx-font-size:11px;");
        }
    }

    private static class WatchlistCell extends ListCell<WatchlistItem> {
        @Override protected void updateItem(WatchlistItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setStyle("-fx-background-color:#12121e;"); return; }
            setText("  " + item.getTicker());
            setStyle("-fx-background-color:#12121e;-fx-text-fill:#c0c0e0;-fx-font-size:12px;");
        }
    }

    private static class NewsCell extends ListCell<NewsItem> {
        @Override protected void updateItem(NewsItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null);
                setStyle("-fx-background-color:#12121e;"); return; }

            VBox box = new VBox(3);
            box.setPadding(new Insets(6, 10, 6, 10));

            // Sentiment badge colour
            String sentiment = item.getSentimentLabel();
            String badgeColor = switch (sentiment.toLowerCase()) {
                case "bullish", "somewhat bullish" -> "#4caf50";
                case "bearish", "somewhat bearish" -> "#ff5252";
                default -> "#8888a0";
            };

            Label titleLbl = new Label(item.getTitle());
            titleLbl.setWrapText(true);
            titleLbl.setStyle("-fx-text-fill:#e0e0e0;-fx-font-size:12px;-fx-font-weight:bold;");
            titleLbl.setMaxWidth(Double.MAX_VALUE);

            Label metaLbl = new Label(
                    "  " + item.getSource() + "  ·  " + item.getFormattedDate()
                    + "    " + sentiment);
            metaLbl.setStyle("-fx-text-fill:" + badgeColor + ";-fx-font-size:10px;");

            box.getChildren().addAll(titleLbl, metaLbl);
            box.setStyle("-fx-background-color:#12121e;-fx-border-color:#2a2a45;"
                    + "-fx-border-width:0 0 1 0;");

            setText(null);
            setGraphic(box);
            setStyle("-fx-background-color:#12121e;");

            // Open in browser on click
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !item.getUrl().isEmpty()) {
                    try { Desktop.getDesktop().browse(new URI(item.getUrl())); }
                    catch (Exception ex) { System.err.println("[News] Cannot open URL: " + ex.getMessage()); }
                }
            });
        }
    }
}
