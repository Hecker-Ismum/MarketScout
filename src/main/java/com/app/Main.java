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
 * MarketScout — retro CRT terminal-themed JavaFX market tracker.
 *
 * <h3>UI Theme</h3>
 * Phosphor-green on near-black; Courier New monospace throughout;
 * ASCII box-drawing chars for section decorations.
 *
 * <h3>Features</h3>
 * Fetch/save OHLC data · Watchlist · Timeframe selectors ·
 * SMA/EMA overlays · CSV export · Auto-refresh · Price alerts ·
 * Toast notifications · Portfolio P&amp;L · News feed
 */
public class Main extends Application {

    // ─────────────────────────────────────────────────────────────────────────
    // Retro colour palette (phosphor green CRT)
    // ─────────────────────────────────────────────────────────────────────────
    private static final String BG_MAIN   = "#080808";
    private static final String BG_SIDE   = "#0a0f00";
    private static final String BG_LIST   = "#060a00";
    private static final String BG_INPUT  = "#000d00";
    private static final String BG_TOOLBR = "#0d1a00";
    private static final String BD_DIM    = "#002a00";
    private static final String BD_MID    = "#005500";
    private static final String TX_DIM    = "#005a00";
    private static final String TX_MID    = "#009922";
    private static final String TX_MAIN   = "#00cc33";
    private static final String TX_BRIGHT = "#00ff41";
    private static final String TX_AMBER  = "#ffaa00";
    private static final String TX_DANGER = "#ff3300";
    private static final String FONT      = "'Courier New'";

    // ── Services ──────────────────────────────────────────────────────────────
    private final DatabaseManager db          = new DatabaseManager();
    private final ApiFetcher      apiFetcher  = new ApiFetcher();
    private final NewsFetcher     newsFetcher = new NewsFetcher();
    private final SettingsManager settings    = SettingsManager.getInstance();
    private       PriceScheduler  scheduler   = new PriceScheduler();

    // ── State ─────────────────────────────────────────────────────────────────
    private Stage          primaryStage;
    private String         currentTicker  = "";
    private double         previousPrice  = 0.0;
    private List<PricePoint> allData      = new ArrayList<>();
    private List<PricePoint> filteredData = new ArrayList<>();
    private int            currentDays    = 0;
    private boolean        showSMA20      = false;
    private boolean        showSMA50      = false;
    private boolean        showEMA20      = false;

    // ── UI – sidebar ──────────────────────────────────────────────────────────
    private TextField           tickerField;
    private TextField           apiKeyField;
    private Button              fetchButton;
    private ListView<WatchlistItem> watchlistView;
    private Label               currentPriceLabel;
    private Label               priceChangeLabel;
    private TextField           intervalField;
    private Button              startStopButton;
    private TextField           alertTargetField;
    private ListView<Alert>     alertsListView;

    // ── UI – chart tab ────────────────────────────────────────────────────────
    private LineChart<String, Number> priceChart;
    private ProgressIndicator         chartSpinner;
    private Button btn1W, btn1M, btn3M, btn1Y, btnAll;
    private CheckBox sma20Box, sma50Box, ema20Box;

    // ── UI – portfolio tab ────────────────────────────────────────────────────
    private TableView<PortfolioRow>          portfolioTable;
    private ObservableList<PortfolioRow>     portfolioRows = FXCollections.observableArrayList();
    private TextField portTickerField, portQtyField, portBuyField;
    private Label     totalValueLabel, totalPnlLabel;

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
        root.setStyle("-fx-background-color:" + BG_MAIN + ";");
        root.setTop(buildHeader());
        root.setLeft(buildSidebar());
        root.setCenter(buildCenterTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1200, 720);
        scene.setFill(Color.web(BG_MAIN));

        // Load external retro CSS (handles chart, tab-pane, scrollbar, table, list)
        try {
            String css = getClass().getResource("/retro.css").toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception e) {
            System.err.println("[UI] Could not load retro.css: " + e.getMessage());
        }

        stage.setTitle("MARKETSCOUT v2.0 — Stock & Crypto Terminal");
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

        apiKeyField.focusedProperty().addListener((obs, was, isNow) -> {
            if (!isNow) settings.set(SettingsManager.KEY_API_KEY, apiKeyField.getText().trim());
        });
        intervalField.focusedProperty().addListener((obs, was, isNow) -> {
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

    /** Terminal-style title bar. */
    private HBox buildHeader() {
        HBox hdr = new HBox(12);
        hdr.setAlignment(Pos.CENTER_LEFT);
        hdr.setPadding(new Insets(10, 20, 10, 20));
        hdr.setStyle(
                "-fx-background-color:" + BG_SIDE + ";"
                + "-fx-border-color:" + BD_MID + ";"
                + "-fx-border-width:0 0 2 0;");

        Label logo = new Label(">_  MARKETSCOUT");
        logo.setStyle("-fx-text-fill:" + TX_BRIGHT + ";"
                + "-fx-font-size:20px;-fx-font-weight:bold;"
                + "-fx-font-family:" + FONT + ";");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label tagline = new Label("[ STOCK & CRYPTO TERMINAL  //  v2.0 ]");
        tagline.setStyle("-fx-text-fill:" + TX_DIM + ";"
                + "-fx-font-size:11px;-fx-font-family:" + FONT + ";");

        hdr.getChildren().addAll(logo, spacer, tagline);
        return hdr;
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────

    private ScrollPane buildSidebar() {
        VBox sb = new VBox(5);
        sb.setPadding(new Insets(10, 8, 10, 8));
        sb.setPrefWidth(215);
        sb.setStyle("-fx-background-color:" + BG_SIDE + ";");

        // ── Symbol ───────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("TICKER"));
        tickerField = field("e.g. AAPL, BTC, IBM");
        tickerField.setOnAction(e -> performFetch());
        apiKeyField = field("ALPHA VANTAGE API KEY");
        apiKeyField.setTooltip(new Tooltip("Free key: alphavantage.co\nDemo key only works for IBM"));
        fetchButton = accentBtn("[ FETCH & SAVE ]");
        fetchButton.setOnAction(e -> performFetch());
        sb.getChildren().addAll(tickerField,
                subLabel("// crypto: no key needed"),
                apiKeyField,
                subLabel("// blank -> demo (IBM only)"),
                fetchButton);
        sb.getChildren().add(divider());

        // ── Watchlist ─────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("WATCHLIST"));
        HBox watchRow = new HBox(4);
        TextField watchField = field("ADD TICKER...");
        watchField.setPrefWidth(115);
        Button watchAdd = secondaryBtn("[+]");
        watchAdd.setPrefWidth(36);
        watchAdd.setOnAction(e -> {
            String t = watchField.getText().trim().toUpperCase();
            if (!t.isEmpty()) { db.addToWatchlist(t); watchField.clear(); refreshWatchlistView(); }
        });
        watchRow.getChildren().addAll(watchField, watchAdd);

        watchlistView = new ListView<>();
        watchlistView.setPrefHeight(95);
        watchlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
                if (sel != null) loadFromWatchlist(sel.getTicker());
            }
        });
        Button watchDel = dangerBtn("[DEL]");
        watchDel.setOnAction(e -> {
            WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
            if (sel != null) { db.removeFromWatchlist(sel.getId()); refreshWatchlistView(); }
        });
        sb.getChildren().addAll(watchRow, watchlistView, watchDel,
                subLabel("// double-click to load"));
        sb.getChildren().add(divider());

        // ── Live Price ────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("LIVE PRICE"));
        currentPriceLabel = new Label("--------");
        currentPriceLabel.setStyle("-fx-text-fill:" + TX_BRIGHT + ";"
                + "-fx-font-size:22px;-fx-font-weight:bold;"
                + "-fx-font-family:" + FONT + ";");
        priceChangeLabel = new Label("");
        priceChangeLabel.setStyle("-fx-text-fill:" + TX_MID + ";"
                + "-fx-font-size:10px;-fx-font-family:" + FONT + ";");
        sb.getChildren().addAll(currentPriceLabel, priceChangeLabel);
        sb.getChildren().add(divider());

        // ── Auto-Refresh ──────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("AUTO-REFRESH"));
        HBox intRow = new HBox(4);
        intRow.setAlignment(Pos.CENTER_LEFT);
        Label evLbl = new Label("EVERY");
        evLbl.setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-size:10px;-fx-font-family:" + FONT + ";");
        intervalField = field("5");
        intervalField.setPrefWidth(40);
        Label minLbl = new Label("MIN");
        minLbl.setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-size:10px;-fx-font-family:" + FONT + ";");
        intRow.getChildren().addAll(evLbl, intervalField, minLbl);
        startStopButton = new Button("[ START ]");
        startStopButton.setMaxWidth(Double.MAX_VALUE);
        startStopButton.setStyle(schedulerStyle(false));
        startStopButton.setOnAction(e -> toggleScheduler());
        sb.getChildren().addAll(intRow, startStopButton);
        sb.getChildren().add(divider());

        // ── Alerts ───────────────────────────────────────────────────────────
        sb.getChildren().add(sectionLabel("PRICE ALERTS"));
        sb.getChildren().add(subLabel("// triggers when price >= target"));
        alertTargetField = field("TARGET PRICE...");
        Button addAlertBtn = secondaryBtn("[+] ADD ALERT");
        addAlertBtn.setOnAction(e -> addAlert());
        alertsListView = new ListView<>();
        alertsListView.setPrefHeight(110);
        alertsListView.setCellFactory(lv -> new AlertCell());
        Button delAlertBtn = dangerBtn("[DEL] REMOVE");
        delAlertBtn.setOnAction(e -> deleteAlert());
        sb.getChildren().addAll(alertTargetField, addAlertBtn, alertsListView, delAlertBtn);

        ScrollPane scroll = new ScrollPane(sb);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background-color:" + BG_SIDE + ";-fx-background:" + BG_SIDE + ";");
        return scroll;
    }

    // ── Center tabs ───────────────────────────────────────────────────────────

    private TabPane buildCenterTabs() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:" + BG_MAIN + ";");

        tabs.getTabs().addAll(
                new Tab("▸ CHART",     buildChartPane()),
                new Tab("▸ PORTFOLIO", buildPortfolioPane()),
                new Tab("▸ NEWS",      buildNewsPane())
        );
        return tabs;
    }

    // ── Chart pane ────────────────────────────────────────────────────────────

    private BorderPane buildChartPane() {
        // Timeframe buttons
        btn1W  = tfBtn("1W");  btn1W.setOnAction(e -> applyTimeframe(7));
        btn1M  = tfBtn("1M");  btn1M.setOnAction(e -> applyTimeframe(30));
        btn3M  = tfBtn("3M");  btn3M.setOnAction(e -> applyTimeframe(90));
        btn1Y  = tfBtn("1Y");  btn1Y.setOnAction(e -> applyTimeframe(365));
        btnAll = tfBtn("ALL"); btnAll.setOnAction(e -> applyTimeframe(0));

        sma20Box = new CheckBox("SMA-20");
        sma50Box = new CheckBox("SMA-50");
        ema20Box = new CheckBox("EMA-20");
        styleCheckBox(sma20Box, TX_AMBER);
        styleCheckBox(sma50Box, "#ff6600");
        styleCheckBox(ema20Box, "#00ccee");
        sma20Box.setOnAction(e -> { showSMA20 = sma20Box.isSelected(); refreshChart(); });
        sma50Box.setOnAction(e -> { showSMA50 = sma50Box.isSelected(); refreshChart(); });
        ema20Box.setOnAction(e -> { showEMA20 = ema20Box.isSelected(); refreshChart(); });

        Button exportBtn = secondaryBtn("[CSV] EXPORT");
        exportBtn.setOnAction(e -> CsvExporter.export(currentTicker, filteredData, primaryStage));

        HBox toolbar = new HBox(7,
                btn1W, btn1M, btn3M, btn1Y, btnAll,
                new Separator(Orientation.VERTICAL),
                sma20Box, sma50Box, ema20Box,
                new Separator(Orientation.VERTICAL),
                exportBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(7, 12, 7, 12));
        toolbar.setStyle("-fx-background-color:" + BG_TOOLBR + ";"
                + "-fx-border-color:" + BD_DIM + ";-fx-border-width:0 0 1 0;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("DATE");
        xAxis.setTickLabelRotation(-45);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("PRICE (USD)");

        priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("// NO DATA — ENTER TICKER AND FETCH");
        priceChart.setAnimated(false);
        priceChart.setCreateSymbols(false);
        priceChart.setLegendVisible(true);
        priceChart.setStyle("-fx-background-color:" + BG_MAIN + ";");

        chartSpinner = new ProgressIndicator();
        chartSpinner.setMaxSize(50, 50);
        chartSpinner.setVisible(false);
        chartSpinner.setStyle("-fx-progress-color:" + TX_BRIGHT + ";");

        StackPane chartArea = new StackPane(priceChart, chartSpinner);
        chartArea.setStyle("-fx-background-color:" + BG_MAIN + ";");

        BorderPane bp = new BorderPane();
        bp.setTop(toolbar);
        bp.setCenter(chartArea);
        bp.setStyle("-fx-background-color:" + BG_MAIN + ";");
        return bp;
    }

    // ── Portfolio pane ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private BorderPane buildPortfolioPane() {
        portTickerField = field("TICKER");
        portQtyField    = field("QTY");
        portBuyField    = field("AVG BUY $");
        portTickerField.setPrefWidth(110);
        portQtyField.setPrefWidth(80);
        portBuyField.setPrefWidth(100);

        Button addHoldBtn     = accentBtn("[+] ADD / UPDATE");
        addHoldBtn.setPrefWidth(140);
        addHoldBtn.setOnAction(e -> addPortfolioHolding());

        Button refreshPrices  = secondaryBtn("[~] REFRESH");
        refreshPrices.setOnAction(e -> refreshPortfolioTable());

        Button delHoldBtn     = dangerBtn("[DEL] REMOVE");
        delHoldBtn.setOnAction(e -> {
            PortfolioRow sel = portfolioTable.getSelectionModel().getSelectedItem();
            if (sel != null) { db.deleteHolding(sel.holding.getId()); refreshPortfolioTable(); setStatus("HOLDING REMOVED.", false); }
        });

        HBox formRow = new HBox(7, portTickerField, portQtyField, portBuyField,
                addHoldBtn, refreshPrices, delHoldBtn);
        formRow.setPadding(new Insets(9, 12, 9, 12));
        formRow.setAlignment(Pos.CENTER_LEFT);
        formRow.setStyle("-fx-background-color:" + BG_TOOLBR + ";"
                + "-fx-border-color:" + BD_DIM + ";-fx-border-width:0 0 1 0;");

        portfolioTable = new TableView<>(portfolioRows);
        portfolioTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        portfolioTable.setPlaceholder(new Label("// NO HOLDINGS — ADD ONE ABOVE") {{
            setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-family:" + FONT + ";"); }});

        TableColumn<PortfolioRow, String> colTicker  = col("TICKER",   "ticker");
        TableColumn<PortfolioRow, String> colQty     = col("QTY",      "qty");
        TableColumn<PortfolioRow, String> colAvg     = col("AVG BUY",  "avgBuy");
        TableColumn<PortfolioRow, String> colCurrent = col("CURRENT",  "current");
        TableColumn<PortfolioRow, String> colValue   = col("VALUE",    "value");
        TableColumn<PortfolioRow, String> colPnl     = col("P&L",      "pnl");
        TableColumn<PortfolioRow, String> colPnlPct  = col("P&L %",   "pnlPct");

        colPnl.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.startsWith("+")
                        ? "-fx-text-fill:#00ff41;-fx-font-weight:bold;-fx-font-family:" + FONT + ";"
                        : "-fx-text-fill:" + TX_DANGER + ";-fx-font-weight:bold;-fx-font-family:" + FONT + ";");
            }
        });
        colPnlPct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(item.startsWith("+")
                        ? "-fx-text-fill:#00ff41;-fx-font-family:" + FONT + ";"
                        : "-fx-text-fill:" + TX_DANGER + ";-fx-font-family:" + FONT + ";");
            }
        });

        portfolioTable.getColumns().addAll(
                colTicker, colQty, colAvg, colCurrent, colValue, colPnl, colPnlPct);

        totalValueLabel = new Label("TOTAL VALUE: --------");
        totalValueLabel.setStyle("-fx-text-fill:" + TX_BRIGHT + ";"
                + "-fx-font-size:13px;-fx-font-weight:bold;-fx-font-family:" + FONT + ";");
        totalPnlLabel = new Label("TOTAL P&L: --------");
        totalPnlLabel.setStyle("-fx-text-fill:" + TX_MID + ";"
                + "-fx-font-size:12px;-fx-font-family:" + FONT + ";");

        HBox totalsBar = new HBox(30, totalValueLabel, totalPnlLabel);
        totalsBar.setPadding(new Insets(9, 12, 9, 12));
        totalsBar.setStyle("-fx-background-color:#060a00;"
                + "-fx-border-color:" + BD_DIM + ";-fx-border-width:1 0 0 0;");

        BorderPane bp = new BorderPane();
        bp.setTop(formRow);
        bp.setCenter(portfolioTable);
        bp.setBottom(totalsBar);
        bp.setStyle("-fx-background-color:" + BG_LIST + ";");
        return bp;
    }

    // ── News pane ─────────────────────────────────────────────────────────────

    private BorderPane buildNewsPane() {
        TextField newsTickerField = field("TICKER (e.g. IBM)");
        newsTickerField.setPrefWidth(130);
        Button fetchNewsBtn = accentBtn("[FETCH] NEWS");
        fetchNewsBtn.setPrefWidth(120);
        newsSpinner = new ProgressIndicator();
        newsSpinner.setMaxSize(22, 22);
        newsSpinner.setVisible(false);
        newsSpinner.setStyle("-fx-progress-color:" + TX_BRIGHT + ";");
        newsStatusLabel = new Label("// IBM WORKS WITH DEMO KEY — ADD YOUR KEY FOR OTHER TICKERS");
        newsStatusLabel.setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-size:10px;-fx-font-family:" + FONT + ";");

        fetchNewsBtn.setOnAction(e -> {
            String t = newsTickerField.getText().trim().toUpperCase();
            if (!t.isEmpty()) fetchNews(t);
        });

        HBox newsToolbar = new HBox(8, newsTickerField, fetchNewsBtn, newsSpinner, newsStatusLabel);
        newsToolbar.setPadding(new Insets(9, 12, 9, 12));
        newsToolbar.setAlignment(Pos.CENTER_LEFT);
        newsToolbar.setStyle("-fx-background-color:" + BG_TOOLBR + ";"
                + "-fx-border-color:" + BD_DIM + ";-fx-border-width:0 0 1 0;");

        newsListView = new ListView<>();
        newsListView.setCellFactory(lv -> new NewsCell());
        newsListView.setPlaceholder(new Label("// NO NEWS LOADED") {{
            setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-family:" + FONT + ";"); }});

        BorderPane bp = new BorderPane();
        bp.setTop(newsToolbar);
        bp.setCenter(newsListView);
        bp.setStyle("-fx-background-color:" + BG_LIST + ";");
        return bp;
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private HBox buildStatusBar() {
        Label prompt = new Label(">_");
        prompt.setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-size:11px;-fx-font-family:" + FONT + ";");
        statusLabel = new Label("SYSTEM READY. ENTER A TICKER AND PRESS [FETCH & SAVE].");
        statusLabel.setStyle("-fx-text-fill:" + TX_MID + ";-fx-font-size:11px;-fx-font-family:" + FONT + ";");
        HBox bar = new HBox(6, prompt, statusLabel);
        bar.setPadding(new Insets(5, 14, 5, 14));
        bar.setStyle("-fx-background-color:#060a00;"
                + "-fx-border-color:" + BD_DIM + ";-fx-border-width:1 0 0 0;");
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATURE LOGIC  (unchanged from non-retro version)
    // ─────────────────────────────────────────────────────────────────────────

    private void performFetch() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("ERR // ENTER A TICKER SYMBOL.", true); return; }
        boolean crypto = apiFetcher.isCrypto(ticker);
        String  src    = crypto ? "CoinGecko" : "Alpha Vantage";

        fetchButton.setDisable(true);
        chartSpinner.setVisible(true);
        setStatus("FETCHING " + ticker + " FROM " + src + "...", false);

        String apiKey = apiKeyField.getText().trim();
        settings.set(SettingsManager.KEY_API_KEY, apiKey);

        new Thread(() -> {
            List<PricePoint> data = apiFetcher.fetchHistory(ticker, apiKey);
            Platform.runLater(() -> {
                fetchButton.setDisable(false);
                chartSpinner.setVisible(false);
                if (data == null || data.isEmpty()) {
                    setStatus("ERR // " + (apiFetcher.getLastError().isEmpty()
                            ? "NO DATA FOR " + ticker : apiFetcher.getLastError().toUpperCase()), true);
                } else {
                    onDataReceived(ticker, data);
                    setStatus("OK  // " + data.size() + " POINTS FOR " + ticker + " — SAVED.", false);
                }
            });
        }).start();
    }

    private void onDataReceived(String ticker, List<PricePoint> data) {
        currentTicker = ticker;
        allData       = data;
        db.savePriceHistory(ticker, data);
        applyTimeframe(currentDays);
        checkAlerts(data);
    }

    private void loadFromWatchlist(String ticker) {
        tickerField.setText(ticker);
        List<PricePoint> cached = db.getPriceHistory(ticker);
        if (!cached.isEmpty()) {
            allData = cached;
            currentTicker = ticker;
            applyTimeframe(currentDays);
            updateCurrentPrice(allData);
            setStatus("OK  // LOADED " + cached.size() + " CACHED POINTS FOR "
                    + ticker + " — CLICK FETCH TO UPDATE.", false);
        } else {
            performFetch();
        }
    }

    private void applyTimeframe(int days) {
        currentDays  = days;
        filteredData = ChartDataHelper.filterByDays(allData, days);
        refreshChart();
        updateCurrentPrice(filteredData);
        List.of(btn1W, btn1M, btn3M, btn1Y, btnAll).forEach(b -> b.setStyle(tfBtnStyle(false)));
        Button active = switch (days) {
            case 7   -> btn1W;
            case 30  -> btn1M;
            case 90  -> btn3M;
            case 365 -> btn1Y;
            default  -> btnAll;
        };
        if (active != null) active.setStyle(tfBtnStyle(true));
    }

    private void refreshChart() {
        if (filteredData == null || filteredData.isEmpty()) return;
        priceChart.getData().clear();

        XYChart.Series<String, Number> price = new XYChart.Series<>();
        price.setName(currentTicker + " CLOSE");
        int step = Math.max(1, filteredData.size() / 150);
        for (int i = 0; i < filteredData.size(); i += step)
            price.getData().add(new XYChart.Data<>(filteredData.get(i).getDate(), filteredData.get(i).getPrice()));

        if (!filteredData.isEmpty()) {
            PricePoint last = filteredData.get(filteredData.size() - 1);
            if (price.getData().isEmpty() || !price.getData()
                    .get(price.getData().size() - 1).getXValue().equals(last.getDate()))
                price.getData().add(new XYChart.Data<>(last.getDate(), last.getPrice()));
        }
        priceChart.getData().add(price);

        if (showSMA20) priceChart.getData().add(ChartDataHelper.computeSMA(filteredData, 20, "SMA-20"));
        if (showSMA50) priceChart.getData().add(ChartDataHelper.computeSMA(filteredData, 50, "SMA-50"));
        if (showEMA20) priceChart.getData().add(ChartDataHelper.computeEMA(filteredData, 20, "EMA-20"));

        priceChart.setTitle("// " + currentTicker + " — PRICE HISTORY");
    }

    private void updateCurrentPrice(List<PricePoint> data) {
        if (data == null || data.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        currentPriceLabel.setText(String.format("$%.2f", latest));
        if (previousPrice > 0) {
            double delta = latest - previousPrice;
            double pct   = (delta / previousPrice) * 100.0;
            String sign  = delta >= 0 ? "+" : "";
            String color = delta >= 0 ? TX_BRIGHT : TX_DANGER;
            priceChangeLabel.setText(String.format("%s$%.2f  (%s%.2f%%)", sign, delta, sign, pct));
            priceChangeLabel.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;-fx-font-family:" + FONT + ";");
        }
        previousPrice = latest;
    }

    private void addAlert() {
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("ERR // ENTER A TICKER FIRST.", true); return; }
        try {
            double target = Double.parseDouble(alertTargetField.getText().trim());
            if (target <= 0) throw new NumberFormatException();
            db.addAlert(ticker, target);
            alertTargetField.clear();
            refreshAlertsList();
            setStatus(String.format("OK  // ALERT SET: %s @ $%.2f", ticker, target), false);
        } catch (NumberFormatException ex) {
            setStatus("ERR // ENTER A VALID POSITIVE NUMBER.", true);
        }
    }

    private void deleteAlert() {
        Alert sel = alertsListView.getSelectionModel().getSelectedItem();
        if (sel == null) { setStatus("ERR // SELECT AN ALERT TO DELETE.", true); return; }
        db.deleteAlert(sel.getId());
        refreshAlertsList();
        setStatus("OK  // ALERT DELETED.", false);
    }

    private void checkAlerts(List<PricePoint> data) {
        if (data == null || data.isEmpty() || currentTicker.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        for (Alert a : db.getAllAlerts()) {
            if (!a.getTicker().equalsIgnoreCase(currentTicker)) continue;
            if (latest >= a.getTargetPrice()) {
                String msg = String.format("%s HIT $%.2f (TARGET $%.2f)",
                        currentTicker, latest, a.getTargetPrice());
                setStatus(">> ALERT: " + msg, false);
                NotificationService.show("PRICE ALERT", msg);
            }
        }
        refreshAlertsList();
    }

    private void toggleScheduler() {
        if (scheduler.isRunning()) {
            scheduler.stop();
            scheduler = new PriceScheduler();
            startStopButton.setText("[ START ]");
            startStopButton.setStyle(schedulerStyle(false));
            setStatus("OK  // AUTO-REFRESH STOPPED.", false);
            return;
        }
        String ticker = tickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("ERR // ENTER A TICKER FIRST.", true); return; }
        long interval = 5;
        try { interval = Math.max(1, Long.parseLong(intervalField.getText().trim())); }
        catch (NumberFormatException ignored) { intervalField.setText("5"); }

        long   fin = interval;
        String key = apiKeyField.getText().trim();
        scheduler.startPriceFetching(apiFetcher, ticker, key, fin, data -> {
            if (data != null && !data.isEmpty()) {
                onDataReceived(ticker, data);
                setStatus("OK  // AUTO-REFRESHED " + ticker + " — " + data.size() + " POINTS.", false);
            } else {
                setStatus("ERR // AUTO-REFRESH FAILED: " + apiFetcher.getLastError().toUpperCase(), true);
            }
        });
        startStopButton.setText("[ STOP ]");
        startStopButton.setStyle(schedulerStyle(true));
        setStatus("OK  // AUTO-REFRESH STARTED: " + ticker + " EVERY " + fin + " MIN.", false);
    }

    private void addPortfolioHolding() {
        String ticker = portTickerField.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("ERR // ENTER A TICKER.", true); return; }
        try {
            double qty = Double.parseDouble(portQtyField.getText().trim());
            double avg = Double.parseDouble(portBuyField.getText().trim());
            if (qty <= 0 || avg <= 0) throw new NumberFormatException();
            db.saveHolding(ticker, qty, avg);
            portTickerField.clear(); portQtyField.clear(); portBuyField.clear();
            refreshPortfolioTable();
            setStatus(String.format("OK  // HOLDING SAVED: %.4f %s @ $%.2f", qty, ticker, avg), false);
        } catch (NumberFormatException ex) {
            setStatus("ERR // ENTER VALID POSITIVE NUMBERS FOR QTY AND PRICE.", true);
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
        String pnlColor    = totalPnl >= 0 ? TX_BRIGHT : TX_DANGER;

        totalValueLabel.setText(String.format("TOTAL VALUE: $%,.2f", totalValue));
        totalPnlLabel.setText(String.format("TOTAL P&L: %s$%,.2f (%s%.2f%%)",
                sign, totalPnl, sign, totalPnlPct));
        totalPnlLabel.setStyle("-fx-text-fill:" + pnlColor
                + ";-fx-font-size:12px;-fx-font-family:" + FONT + ";");
    }

    private void fetchNews(String ticker) {
        newsSpinner.setVisible(true);
        newsListView.getItems().clear();
        newsStatusLabel.setText("// FETCHING NEWS FOR " + ticker + "...");
        String key = apiKeyField.getText().trim();
        new Thread(() -> {
            List<NewsItem> items = newsFetcher.fetchNews(ticker, key);
            Platform.runLater(() -> {
                newsSpinner.setVisible(false);
                if (items.isEmpty()) {
                    newsStatusLabel.setText("ERR // " + newsFetcher.getLastError().toUpperCase());
                } else {
                    newsListView.getItems().setAll(items);
                    newsStatusLabel.setText("OK  // " + items.size() + " ARTICLES FOR " + ticker);
                }
            });
        }).start();
    }

    // ── Refresh helpers ───────────────────────────────────────────────────────

    private void refreshWatchlistView() { watchlistView.getItems().setAll(db.getWatchlist()); }
    private void refreshAlertsList()    { alertsListView.getItems().setAll(db.getAllAlerts()); }

    private void setStatus(String msg, boolean error) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (error ? TX_DANGER : TX_MID)
                + ";-fx-font-size:11px;-fx-font-family:" + FONT + ";");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STYLE HELPERS — Retro terminal theme
    // ─────────────────────────────────────────────────────────────────────────

    private Label sectionLabel(String text) {
        Label l = new Label("■ " + text);
        l.setStyle("-fx-text-fill:" + TX_BRIGHT + ";"
                + "-fx-font-size:10px;-fx-font-weight:bold;"
                + "-fx-font-family:" + FONT + ";"
                + "-fx-padding:6 0 1 0;");
        return l;
    }

    private Label subLabel(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill:" + TX_DIM + ";-fx-font-size:8px;-fx-font-family:" + FONT + ";");
        return l;
    }

    private Separator divider() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BD_DIM + ";");
        VBox.setMargin(s, new Insets(5, 0, 4, 0));
        return s;
    }

    private TextField field(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setStyle("-fx-background-color:" + BG_INPUT + ";"
                + "-fx-text-fill:" + TX_MAIN + ";"
                + "-fx-prompt-text-fill:" + TX_DIM + ";"
                + "-fx-background-radius:0;"
                + "-fx-border-color:" + BD_DIM + ";"
                + "-fx-border-radius:0;"
                + "-fx-font-size:11px;"
                + "-fx-font-family:" + FONT + ";");
        return tf;
    }

    private Button accentBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:" + BG_INPUT + ";"
                + "-fx-text-fill:" + TX_BRIGHT + ";"
                + "-fx-font-weight:bold;"
                + "-fx-background-radius:0;"
                + "-fx-border-color:" + TX_MID + ";"
                + "-fx-border-radius:0;"
                + "-fx-font-size:11px;"
                + "-fx-font-family:" + FONT + ";"
                + "-fx-cursor:hand;");
        return b;
    }

    private Button secondaryBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:" + BG_INPUT + ";"
                + "-fx-text-fill:" + TX_MID + ";"
                + "-fx-background-radius:0;"
                + "-fx-border-color:" + BD_DIM + ";"
                + "-fx-border-radius:0;"
                + "-fx-font-size:11px;"
                + "-fx-font-family:" + FONT + ";"
                + "-fx-cursor:hand;");
        return b;
    }

    private Button dangerBtn(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle("-fx-background-color:" + BG_INPUT + ";"
                + "-fx-text-fill:" + TX_DANGER + ";"
                + "-fx-background-radius:0;"
                + "-fx-border-color:#3a0a00;"
                + "-fx-border-radius:0;"
                + "-fx-font-size:11px;"
                + "-fx-font-family:" + FONT + ";"
                + "-fx-cursor:hand;");
        return b;
    }

    private Button tfBtn(String text) {
        Button b = new Button(text);
        b.setStyle(tfBtnStyle(false));
        return b;
    }

    private String tfBtnStyle(boolean active) {
        return active
                ? "-fx-background-color:#001a00;-fx-text-fill:" + TX_BRIGHT + ";"
                  + "-fx-font-weight:bold;-fx-background-radius:0;"
                  + "-fx-border-color:" + TX_MID + ";-fx-border-radius:0;"
                  + "-fx-font-size:11px;-fx-font-family:" + FONT + ";-fx-cursor:hand;"
                : "-fx-background-color:" + BG_INPUT + ";-fx-text-fill:" + TX_DIM + ";"
                  + "-fx-background-radius:0;-fx-border-color:" + BD_DIM + ";"
                  + "-fx-border-radius:0;-fx-font-size:11px;-fx-font-family:" + FONT + ";-fx-cursor:hand;";
    }

    private String schedulerStyle(boolean running) {
        return running
                ? "-fx-background-color:#200a00;-fx-text-fill:" + TX_AMBER + ";"
                  + "-fx-font-weight:bold;-fx-background-radius:0;"
                  + "-fx-border-color:#553300;-fx-border-radius:0;"
                  + "-fx-font-size:11px;-fx-font-family:" + FONT + ";"
                : "-fx-background-color:#001500;-fx-text-fill:" + TX_BRIGHT + ";"
                  + "-fx-font-weight:bold;-fx-background-radius:0;"
                  + "-fx-border-color:" + BD_MID + ";-fx-border-radius:0;"
                  + "-fx-font-size:11px;-fx-font-family:" + FONT + ";";
    }

    private void styleCheckBox(CheckBox cb, String accentColor) {
        cb.setStyle("-fx-text-fill:" + accentColor
                + ";-fx-font-size:11px;-fx-font-family:" + FONT + ";");
    }

    private TableColumn<PortfolioRow, String> col(String header, String field) {
        TableColumn<PortfolioRow, String> col = new TableColumn<>(header);
        col.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().get(field)));
        return col;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER: PortfolioRow
    // ─────────────────────────────────────────────────────────────────────────

    static class PortfolioRow {
        final PortfolioHolding holding;
        final double currentPrice, currentValue, pnl, pnlPct;

        PortfolioRow(PortfolioHolding h, double currentPrice) {
            this.holding      = h;
            this.currentPrice = currentPrice;
            this.currentValue = currentPrice > 0 ? h.getQuantity() * currentPrice : 0;
            this.pnl          = currentValue > 0 ? currentValue - h.getCostBasis() : 0;
            this.pnlPct       = h.getCostBasis() > 0 ? (pnl / h.getCostBasis()) * 100 : 0;
        }

        String get(String f) {
            String sign = pnl >= 0 ? "+" : "";
            return switch (f) {
                case "ticker"  -> holding.getTicker();
                case "qty"     -> String.format("%.4f", holding.getQuantity());
                case "avgBuy"  -> String.format("$%.2f", holding.getAvgBuyPrice());
                case "current" -> currentPrice > 0 ? String.format("$%.2f", currentPrice) : "N/A";
                case "value"   -> currentValue > 0 ? String.format("$%,.2f", currentValue) : "N/A";
                case "pnl"     -> currentValue > 0 ? String.format("%s$%,.2f", sign, pnl) : "N/A";
                case "pnlPct"  -> currentValue > 0 ? String.format("%s%.2f%%", sign, pnlPct) : "N/A";
                default        -> "";
            };
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER: Custom list cells
    // ─────────────────────────────────────────────────────────────────────────

    private class AlertCell extends ListCell<Alert> {
        @Override protected void updateItem(Alert a, boolean empty) {
            super.updateItem(a, empty);
            if (empty || a == null) {
                setText(null); setStyle("-fx-background-color:" + BG_LIST + ";"); return;
            }
            setText(a.toString());
            boolean triggered = previousPrice > 0 && previousPrice >= a.getTargetPrice()
                    && a.getTicker().equalsIgnoreCase(currentTicker);
            setStyle(triggered
                    ? "-fx-background-color:#1a0a00;-fx-text-fill:" + TX_AMBER
                      + ";-fx-font-family:" + FONT + ";-fx-font-size:11px;"
                    : "-fx-background-color:" + BG_LIST + ";-fx-text-fill:" + TX_MAIN
                      + ";-fx-font-family:" + FONT + ";-fx-font-size:11px;");
        }
    }

    private static class WatchlistCell extends ListCell<WatchlistItem> {
        @Override protected void updateItem(WatchlistItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null); setStyle("-fx-background-color:" + BG_LIST + ";"); return;
            }
            setText("  > " + item.getTicker());
            setStyle("-fx-background-color:" + BG_LIST + ";-fx-text-fill:" + TX_MAIN
                    + ";-fx-font-family:" + FONT + ";-fx-font-size:11px;");
        }
    }

    private static class NewsCell extends ListCell<NewsItem> {
        @Override protected void updateItem(NewsItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null);
                setStyle("-fx-background-color:" + BG_LIST + ";"); return; }

            String sentiment = item.getSentimentLabel();
            String badge = switch (sentiment.toLowerCase()) {
                case "bullish", "somewhat bullish" -> "[BULL] " + TX_BRIGHT;
                case "bearish", "somewhat bearish" -> "[BEAR] " + TX_DANGER;
                default                            -> "[NEUT] " + TX_MID;
            };
            String[] parts   = badge.split(" ");
            String   badgeTxt = parts[0];
            String   badgeClr = parts[1];

            VBox box = new VBox(3);
            box.setPadding(new Insets(6, 10, 6, 10));

            Label titleLbl = new Label(item.getTitle());
            titleLbl.setWrapText(true);
            titleLbl.setMaxWidth(Double.MAX_VALUE);
            titleLbl.setStyle("-fx-text-fill:" + TX_MAIN + ";-fx-font-size:11px;"
                    + "-fx-font-weight:bold;-fx-font-family:" + FONT + ";");

            Label metaLbl = new Label(
                    badgeTxt + "  " + item.getSource() + "  //  " + item.getFormattedDate());
            metaLbl.setStyle("-fx-text-fill:" + badgeClr + ";-fx-font-size:9px;"
                    + "-fx-font-family:" + FONT + ";");

            box.getChildren().addAll(titleLbl, metaLbl);
            box.setStyle("-fx-background-color:" + BG_LIST + ";"
                    + "-fx-border-color:" + BD_DIM + ";-fx-border-width:0 0 1 0;");

            setText(null);
            setGraphic(box);
            setStyle("-fx-background-color:" + BG_LIST + ";");

            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !item.getUrl().isEmpty()) {
                    try { Desktop.getDesktop().browse(new URI(item.getUrl())); }
                    catch (Exception ex) { System.err.println("[News] Cannot open URL: " + ex.getMessage()); }
                }
            });
        }
    }
}
