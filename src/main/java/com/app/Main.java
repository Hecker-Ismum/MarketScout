package com.app;

// ── JavaFX core ───────────────────────────────────────────────────────────────
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.stage.Stage;
import javafx.scene.Scene;

// ── Layout ────────────────────────────────────────────────────────────────────
import javafx.scene.layout.*;

// ── Controls ──────────────────────────────────────────────────────────────────
import javafx.scene.control.*;
import javafx.scene.control.PasswordField;

// ── Chart ─────────────────────────────────────────────────────────────────────
import javafx.scene.chart.*;

// ── Geometry / Paint ─────────────────────────────────────────────────────────
import javafx.geometry.*;
import javafx.scene.paint.Color;

// ── Java standard ─────────────────────────────────────────────────────────────
import java.awt.Desktop;
import java.net.URI;
import java.util.*;

/**
 * MarketScout — Minimal retro CRT terminal layout.
 *
 * <h3>Layout</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┬────────┐
 * │ >_ MARKETSCOUT │ [Asset▾] [↺] │ 1W 1M 3M 1Y ALL │ ☐ SMA …│KEY│CSV│ │ PRICE│
 * ├─────────────────────────────────────────────────────────────────┤ $xxx  │
 * │                                                                 │ +1.2% │
 * │                    CHART  (fills window)                        │────── │
 * │                                                                 │WATCH  │
 * │                                                                 │LIST   │
 * ├──────────────────────────────────────────────────────────────── ┴────── ┤
 * │ ▸ ALERTS  ▸ PORTFOLIO  ▸ NEWS ──── status ──────────── [▲ EXPAND] │
 * └─────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
public class Main extends Application {

    // ─────────────────────────────────────────────────────────────────────────
    // Retro colour palette
    // ─────────────────────────────────────────────────────────────────────────
    private static final String BG    = "#080808";
    private static final String BG_P  = "#0a0f00";
    private static final String BG_IN = "#000d00";
    private static final String BD    = "#002a00";
    private static final String BD_M  = "#005500";
    private static final String TX_D  = "#005a00";
    private static final String TX_M  = "#009922";
    private static final String TX    = "#00cc33";
    private static final String TX_B  = "#00ff41";
    private static final String TX_A  = "#ffaa00";   // amber
    private static final String TX_R  = "#ff3300";   // red / danger
    private static final String FNT   = "'Courier New'";

    // ─────────────────────────────────────────────────────────────────────────
    // Pre-populated asset list  (header rows start with "──")
    // ─────────────────────────────────────────────────────────────────────────
    private static final List<String> ASSETS = List.of(
        "── STOCKS ──",
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA",
        "NVDA", "META", "IBM",  "JPM",  "NFLX",
        "BABA", "V",    "BAC",  "DIS",  "UBER",
        "── CRYPTO ──",
        "BTC",  "ETH",  "BNB",  "SOL",  "ADA",
        "DOGE", "XRP",  "LTC",  "LINK", "ATOM"
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Services
    // ─────────────────────────────────────────────────────────────────────────
    private final DatabaseManager db          = new DatabaseManager();
    private final ApiFetcher      api         = new ApiFetcher();
    private final NewsFetcher     newsFetcher = new NewsFetcher();
    private final SettingsManager settings    = SettingsManager.getInstance();
    private       PriceScheduler  scheduler   = new PriceScheduler();

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────
    private Stage            primaryStage;
    private String           currentTicker  = "";
    private double           previousPrice  = 0;
    private List<PricePoint> allData        = new ArrayList<>();
    private List<PricePoint> filteredData   = new ArrayList<>();
    private int              currentDays    = 30;          // default: 1-month view
    private boolean          showSMA20, showSMA50, showEMA20;
    private boolean          bottomExpanded = false;
    private int              selectedTab    = 0;

    // ─────────────────────────────────────────────────────────────────────────
    // UI references
    // ─────────────────────────────────────────────────────────────────────────
    // toolbar
    private ComboBox<String>          assetCombo;
    private PasswordField                apiKeyField;
    private Button                    btn1W, btn1M, btn3M, btn1Y, btnAll;
    private CheckBox                  sma20Box, sma50Box, ema20Box;
    private ProgressIndicator         spinner;

    // right panel
    private VBox                      rightPanel;

    // chart
    private LineChart<String, Number> chart;

    // right panel
    private Label                     priceLabel, priceChangeLabel;
    private ListView<WatchlistItem>   watchlistView;

    // bottom strip
    private Label                     statusLabel;
    private Button                    toggleBottomBtn;
    private VBox                      bottomContent;
    private TabPane                   bottomTabPane;

    // bottom tabs
    private TextField                 alertTargetField;
    private ListView<Alert>           alertsListView;
    private TableView<PortfolioRow>   portfolioTable;
    private ObservableList<PortfolioRow> portfolioRows = FXCollections.observableArrayList();
    private TextField                 portTickerF, portQtyF, portBuyF;
    private Label                     totalValueLabel, totalPnlLabel;
    private ListView<NewsItem>        newsListView;
    private Label                     newsStatusLabel;

    // ─────────────────────────────────────────────────────────────────────────
    // Launch
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        db.initializeDatabase();

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + BG + ";");
        root.setTop(buildToolbar());
        root.setCenter(buildChartArea());
        root.setRight(buildRightPanel());
        root.setBottom(buildBottomBar());

        Scene scene = new Scene(root, 1280, 760);
        scene.setFill(Color.web(BG));
        try { scene.getStylesheets().add(getClass().getResource("/retro.css").toExternalForm()); }
        catch (Exception e) { System.err.println("[CSS] " + e.getMessage()); }

        stage.setTitle("MARKETSCOUT v2.0");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(540);
        stage.setOnCloseRequest(e -> { if (scheduler.isRunning()) scheduler.stop(); });
        stage.show();

        // ── Responsive layout bindings (relative to window dimensions) ────────
        // Right panel: 14% of window width (min 145px)
        rightPanel.prefWidthProperty().bind(
                scene.widthProperty().multiply(0.14).map(n -> Math.max(n.doubleValue(), 145.0)));
        // Collapsed bottom content: 32% of window height when expanded
        bottomContent.prefHeightProperty().bind(scene.heightProperty().multiply(0.32));
        // Asset ComboBox: 13% of window width
        assetCombo.prefWidthProperty().bind(scene.widthProperty().multiply(0.13));
        // API key field: 11% of window width
        apiKeyField.prefWidthProperty().bind(scene.widthProperty().multiply(0.11));

        // Restore saved API key
        String savedKey = settings.get(SettingsManager.KEY_API_KEY, "");
        if (!savedKey.isEmpty()) apiKeyField.setText(savedKey);
        apiKeyField.focusedProperty().addListener((o, w, n) -> {
            if (!n) settings.set(SettingsManager.KEY_API_KEY, apiKeyField.getText().trim());
        });

        // Restore last-used ticker from DB cache (no network call on startup)
        String last = settings.get("last.ticker", "");
        if (!last.isEmpty()) {
            List<PricePoint> cached = db.getPriceHistory(last);
            if (!cached.isEmpty()) {
                currentTicker = last;
                allData = cached;
                assetCombo.setValue(last);
                applyTimeframe(currentDays);
                updatePrice(allData);
                setStatus("OK  // LOADED " + cached.size() + " CACHED POINTS FOR " + last
                        + "  —  PRESS [↺] TO REFRESH FROM API.");
            }
        }

        refreshWatchlistView();
        refreshAlertsList();
        refreshPortfolioTable();
    }

    @Override
    public void stop() { if (scheduler.isRunning()) scheduler.stop(); }

    // ─────────────────────────────────────────────────────────────────────────
    // TOOLBAR  (single compact row — all chart controls + API key)
    // ─────────────────────────────────────────────────────────────────────────

    private HBox buildToolbar() {
        // ── Logo ─────────────────────────────────────────────────────────────
        Label logo = new Label(">_ MARKETSCOUT");
        logo.setStyle("-fx-text-fill:" + TX_B + ";-fx-font-size:16px;"
                + "-fx-font-weight:bold;-fx-font-family:" + FNT + ";");

        // ── Asset ComboBox ────────────────────────────────────────────────────
        assetCombo = new ComboBox<>();
        assetCombo.setEditable(true);
        assetCombo.setPrefWidth(165);
        assetCombo.getItems().addAll(ASSETS);
        assetCombo.setPromptText("TICKER...");
        assetCombo.setCellFactory(lv -> new AssetListCell());
        assetCombo.setButtonCell(new AssetButtonCell());
        assetCombo.setTooltip(new Tooltip(
                "Choose from the list or type any ticker.\n"
                + "Crypto (BTC, ETH…) needs no API key.\n"
                + "Stocks need a free Alpha Vantage key."));

        // Auto-fetch when dropdown selection changes
        assetCombo.setOnAction(e -> {
            String v = assetCombo.getValue();
            if (v != null && !v.isBlank() && !v.startsWith("──")) {
                String t = v.trim().toUpperCase();
                assetCombo.setValue(t);
                triggerFetch(t);
            }
        });
        // Also trigger when the user types a ticker and presses Enter
        assetCombo.getEditor().setOnAction(e -> {
            String v = assetCombo.getEditor().getText().trim().toUpperCase();
            if (!v.isEmpty() && !v.startsWith("──")) {
                assetCombo.setValue(v);
                triggerFetch(v);
            }
        });

        // ── Refresh button ────────────────────────────────────────────────────
        Button refreshBtn = retBtn("[↺]", TX_M);
        refreshBtn.setTooltip(new Tooltip("Re-fetch current ticker from the API"));
        refreshBtn.setOnAction(e -> {
            String v = assetCombo.getValue();
            if (v != null && !v.isBlank() && !v.startsWith("──"))
                triggerFetch(v.trim().toUpperCase());
        });

        // ── Timeframe buttons ─────────────────────────────────────────────────
        btn1W  = tfBtn("1W");  btn1W.setOnAction(e -> applyTimeframe(7));
        btn1M  = tfBtn("1M");  btn1M.setOnAction(e -> applyTimeframe(30));
        btn3M  = tfBtn("3M");  btn3M.setOnAction(e -> applyTimeframe(90));
        btn1Y  = tfBtn("1Y");  btn1Y.setOnAction(e -> applyTimeframe(365));
        btnAll = tfBtn("ALL"); btnAll.setOnAction(e -> applyTimeframe(0));
        btn1M.setStyle(tfBtnStyle(true));  // default active

        // ── Indicator checkboxes ──────────────────────────────────────────────
        sma20Box = chk("SMA-20", TX_A);     sma20Box.setOnAction(e -> { showSMA20 = sma20Box.isSelected(); refreshChart(); });
        sma50Box = chk("SMA-50", "#ff6600"); sma50Box.setOnAction(e -> { showSMA50 = sma50Box.isSelected(); refreshChart(); });
        ema20Box = chk("EMA-20", "#00ccee"); ema20Box.setOnAction(e -> { showEMA20 = ema20Box.isSelected(); refreshChart(); });

        // ── Spacer ────────────────────────────────────────────────────────────
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── API key PasswordField ─────────────────────────────────────────────
        Label keyLbl = new Label("KEY:");
        keyLbl.setStyle("-fx-text-fill:" + TX_D + ";-fx-font-size:9px;-fx-font-family:" + FNT + ";");
        apiKeyField = new PasswordField();
        apiKeyField.setPromptText("ALPHA VANTAGE...");
        apiKeyField.setPrefWidth(135);
        apiKeyField.setStyle(
                "-fx-background-color:" + BG_IN + ";-fx-text-fill:" + TX_M + ";"
                + "-fx-prompt-text-fill:" + TX_D + ";-fx-background-radius:0;"
                + "-fx-border-color:" + BD + ";-fx-border-radius:0;"
                + "-fx-font-size:10px;-fx-font-family:" + FNT + ";");
        apiKeyField.setTooltip(new Tooltip(
                "Free key at alphavantage.co\nSaved automatically — only enter once.\nKey is masked for security."));

        // Show / hide key toggle
        final boolean[] keyVisible = {false};
        final TextField keyPlainField = new TextField();
        keyPlainField.setStyle(apiKeyField.getStyle());
        keyPlainField.setPromptText("ALPHA VANTAGE...");
        keyPlainField.setVisible(false);
        keyPlainField.setManaged(false);
        keyPlainField.textProperty().bindBidirectional(apiKeyField.textProperty());
        Button showHideBtn = retBtn("[👁]", TX_D);
        showHideBtn.setTooltip(new Tooltip("Show / hide API key"));
        showHideBtn.setOnAction(e -> {
            keyVisible[0] = !keyVisible[0];
            apiKeyField.setVisible(!keyVisible[0]);  apiKeyField.setManaged(!keyVisible[0]);
            keyPlainField.setVisible(keyVisible[0]); keyPlainField.setManaged(keyVisible[0]);
            showHideBtn.setText(keyVisible[0] ? "[🔒]" : "[👁]");
        });

        // ── CSV export ────────────────────────────────────────────────────────
        Button csvBtn = retBtn("[↓ CSV]", TX_M);
        csvBtn.setTooltip(new Tooltip("Export current chart data to a CSV file"));
        csvBtn.setOnAction(e -> CsvExporter.export(currentTicker, filteredData, primaryStage));

        HBox bar = new HBox(7,
                logo,
                vSep(), assetCombo, refreshBtn,
                vSep(), btn1W, btn1M, btn3M, btn1Y, btnAll,
                vSep(), sma20Box, sma50Box, ema20Box,
                spacer,
                keyLbl, apiKeyField, keyPlainField, showHideBtn, csvBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(7, 12, 7, 14));
        bar.setStyle("-fx-background-color:" + BG_P + ";"
                + "-fx-border-color:" + BD + ";-fx-border-width:0 0 1 0;");
        return bar;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHART AREA  (fills center; spinner overlaid)
    // ─────────────────────────────────────────────────────────────────────────

    private StackPane buildChartArea() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("DATE");
        xAxis.setTickLabelRotation(-45);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("PRICE (USD)");

        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("// SELECT AN ASSET OR TYPE A TICKER ABOVE");
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);
        chart.setStyle("-fx-background-color:" + BG + ";");

        spinner = new ProgressIndicator();
        spinner.setMaxSize(52, 52);
        spinner.setStyle("-fx-progress-color:" + TX_B + ";");
        spinner.setVisible(false);

        StackPane area = new StackPane(chart, spinner);
        area.setStyle("-fx-background-color:" + BG + ";");
        return area;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RIGHT PANEL  (slim: price + watchlist)
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildRightPanel() {
        rightPanel = new VBox(5);
        rightPanel.setPrefWidth(160);
        rightPanel.setPadding(new Insets(10, 8, 10, 8));
        rightPanel.setStyle("-fx-background-color:" + BG_P + ";"
                + "-fx-border-color:" + BD + ";-fx-border-width:0 0 0 1;");

        // Price
        priceLabel = new Label("--------");
        priceLabel.setStyle("-fx-text-fill:" + TX_B + ";-fx-font-size:21px;"
                + "-fx-font-weight:bold;-fx-font-family:" + FNT + ";");
        priceLabel.setWrapText(true);
        priceChangeLabel = new Label("");
        priceChangeLabel.setStyle("-fx-text-fill:" + TX_M + ";-fx-font-size:10px;"
                + "-fx-font-family:" + FNT + ";");

        rightPanel.getChildren().addAll(
                panelLabel("PRICE"), priceLabel, priceChangeLabel, hLine());

        // Watchlist
        rightPanel.getChildren().add(panelLabel("WATCHLIST"));
        rightPanel.getChildren().add(subLabel("// double-click to load"));

        watchlistView = new ListView<>();
        watchlistView.setCellFactory(lv -> new WatchlistCell());
        VBox.setVgrow(watchlistView, Priority.ALWAYS);
        watchlistView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    assetCombo.setValue(sel.getTicker());
                    triggerFetch(sel.getTicker());
                }
            }
        });

        // Watchlist add row
        HBox wRow = new HBox(4);
        TextField wField = new TextField();
        wField.setPromptText("ADD...");
        wField.setPrefWidth(88);
        wField.setStyle("-fx-background-color:" + BG_IN + ";-fx-text-fill:" + TX + ";"
                + "-fx-prompt-text-fill:" + TX_D + ";-fx-background-radius:0;"
                + "-fx-border-color:" + BD + ";-fx-border-radius:0;"
                + "-fx-font-size:10px;-fx-font-family:" + FNT + ";");
        Button wAdd = retBtn("[+]", TX_M);
        wAdd.setPrefWidth(38);
        Runnable doAdd = () -> {
            String t = wField.getText().trim().toUpperCase();
            if (!t.isEmpty()) { db.addToWatchlist(t); wField.clear(); refreshWatchlistView(); }
        };
        wAdd.setOnAction(e -> doAdd.run());
        wField.setOnAction(e -> doAdd.run());
        wRow.getChildren().addAll(wField, wAdd);

        Button wDel = retBtn("[DEL]", TX_R);
        wDel.setMaxWidth(Double.MAX_VALUE);
        wDel.setOnAction(e -> {
            WatchlistItem sel = watchlistView.getSelectionModel().getSelectedItem();
            if (sel != null) { db.removeFromWatchlist(sel.getId()); refreshWatchlistView(); }
        });

        rightPanel.getChildren().addAll(watchlistView, wRow, wDel);
        return rightPanel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BOTTOM BAR  (strip always visible; content expands upward)
    // ─────────────────────────────────────────────────────────────────────────

    private VBox buildBottomBar() {
        // Tab strip buttons
        Button alertsBtn = stripBtn("ALERTS");
        Button portBtn   = stripBtn("PORTFOLIO");
        Button newsBtn   = stripBtn("NEWS");

        alertsBtn.setOnAction(e -> toggleOrShow(0));
        portBtn.setOnAction(e  -> toggleOrShow(1));
        newsBtn.setOnAction(e  -> toggleOrShow(2));

        statusLabel = new Label(">_ READY");
        statusLabel.setStyle("-fx-text-fill:" + TX_M + ";-fx-font-size:10px;-fx-font-family:" + FNT + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleBottomBtn = retBtn("[▲ PANEL]", TX_D);
        toggleBottomBtn.setOnAction(e -> toggleOrShow(-1));

        HBox strip = new HBox(6, alertsBtn, portBtn, newsBtn,
                spacer, statusLabel, toggleBottomBtn);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setPadding(new Insets(5, 12, 5, 12));
        strip.setStyle("-fx-background-color:#060a00;"
                + "-fx-border-color:" + BD + ";-fx-border-width:1 0 0 0;");

        // Build tab content (collapsed by default)
        bottomTabPane = buildBottomTabs();
        bottomContent = new VBox(bottomTabPane);
        bottomContent.setVisible(false);
        bottomContent.setManaged(false);
        bottomContent.setPrefHeight(230);

        // Stack: content above strip
        return new VBox(bottomContent, strip);
    }

    /** Toggle collapse OR switch to a specific tab index (-1 = just toggle). */
    private void toggleOrShow(int tabIndex) {
        if (tabIndex >= 0) {
            if (!bottomExpanded) {
                // Expand and show requested tab
                bottomExpanded = true;
                bottomContent.setVisible(true);
                bottomContent.setManaged(true);
                toggleBottomBtn.setText("[▼ PANEL]");
            }
            selectedTab = tabIndex;
            bottomTabPane.getSelectionModel().select(tabIndex);
        } else {
            // Pure toggle
            bottomExpanded = !bottomExpanded;
            bottomContent.setVisible(bottomExpanded);
            bottomContent.setManaged(bottomExpanded);
            toggleBottomBtn.setText(bottomExpanded ? "[▼ PANEL]" : "[▲ PANEL]");
        }
    }

    @SuppressWarnings("unchecked")
    private TabPane buildBottomTabs() {
        // ── ALERTS ───────────────────────────────────────────────────────────
        alertTargetField = inlineField("TARGET PRICE...", 140);
        Button addAlertBtn  = retBtn("[+] ADD",  TX_M);
        Button editAlertBtn = retBtn("[✎] EDIT", TX_A);
        Button delAlertBtn  = retBtn("[DEL]",    TX_R);
        addAlertBtn.setOnAction(e -> addAlert());
        delAlertBtn.setOnAction(e -> deleteAlert());
        editAlertBtn.setOnAction(e -> editAlert());
        editAlertBtn.setTooltip(new Tooltip("Update the target price for the selected alert (SQL UPDATE)"));
        alertsListView = new ListView<>();
        alertsListView.setCellFactory(lv -> new AlertCell());
        VBox.setVgrow(alertsListView, Priority.ALWAYS);

        HBox alertForm = new HBox(6, alertTargetField, addAlertBtn, editAlertBtn, delAlertBtn);

        alertForm.setPadding(new Insets(6, 10, 6, 10));
        alertForm.setStyle("-fx-background-color:" + BG_P + ";"
                + "-fx-border-color:" + BD + ";-fx-border-width:0 0 1 0;");
        VBox alertsPane = new VBox(alertForm, alertsListView);
        VBox.setVgrow(alertsListView, Priority.ALWAYS);
        alertsPane.setStyle("-fx-background-color:" + BG + ";");

        // ── PORTFOLIO ─────────────────────────────────────────────────────────
        portTickerF = inlineField("TICKER", 80);
        portQtyF    = inlineField("QTY",    65);
        portBuyF    = inlineField("AVG BUY $", 90);
        Button addHoldBtn  = retBtn("[+] SAVE", TX_B);
        Button refPriceBtn = retBtn("[~] REFRESH", TX_M);
        Button delHoldBtn  = retBtn("[DEL]", TX_R);
        addHoldBtn.setOnAction(e -> addPortfolioHolding());
        refPriceBtn.setOnAction(e -> refreshPortfolioTable());
        delHoldBtn.setOnAction(e -> {
            PortfolioRow sel = portfolioTable.getSelectionModel().getSelectedItem();
            if (sel != null) { db.deleteHolding(sel.holding.getId()); refreshPortfolioTable(); }
        });
        HBox portForm = new HBox(6, portTickerF, portQtyF, portBuyF,
                addHoldBtn, refPriceBtn, delHoldBtn);
        portForm.setPadding(new Insets(6, 10, 6, 10));
        portForm.setStyle("-fx-background-color:" + BG_P + ";"
                + "-fx-border-color:" + BD + ";-fx-border-width:0 0 1 0;");

        portfolioTable = new TableView<>(portfolioRows);
        portfolioTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        portfolioTable.setPlaceholder(new Label("// NO HOLDINGS") {{
            setStyle("-fx-text-fill:" + TX_D + ";-fx-font-family:" + FNT + ";"); }});
        VBox.setVgrow(portfolioTable, Priority.ALWAYS);

        TableColumn<PortfolioRow, String> cT = col("TICKER",  "ticker");
        TableColumn<PortfolioRow, String> cQ = col("QTY",     "qty");
        TableColumn<PortfolioRow, String> cA = col("AVG BUY", "avgBuy");
        TableColumn<PortfolioRow, String> cC = col("CURRENT", "current");
        TableColumn<PortfolioRow, String> cV = col("VALUE",   "value");
        TableColumn<PortfolioRow, String> cP = col("P&L",     "pnl");
        TableColumn<PortfolioRow, String> cPct = col("P&L %", "pnlPct");

        cP.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty); if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(s.startsWith("+") ? "-fx-text-fill:" + TX_B + ";-fx-font-weight:bold;-fx-font-family:" + FNT + ";"
                                           : "-fx-text-fill:" + TX_R + ";-fx-font-weight:bold;-fx-font-family:" + FNT + ";");
            }
        });
        cPct.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty); if (empty || s == null) { setText(null); setStyle(""); return; }
                setText(s);
                setStyle(s.startsWith("+") ? "-fx-text-fill:" + TX_B + ";-fx-font-family:" + FNT + ";"
                                           : "-fx-text-fill:" + TX_R + ";-fx-font-family:" + FNT + ";");
            }
        });
        portfolioTable.getColumns().addAll(cT, cQ, cA, cC, cV, cP, cPct);

        totalValueLabel = new Label("VALUE: --");
        totalValueLabel.setStyle("-fx-text-fill:" + TX_B + ";-fx-font-size:12px;"
                + "-fx-font-weight:bold;-fx-font-family:" + FNT + ";");
        totalPnlLabel = new Label("P&L: --");
        totalPnlLabel.setStyle("-fx-text-fill:" + TX_M + ";-fx-font-size:11px;-fx-font-family:" + FNT + ";");

        HBox portTotals = new HBox(20, totalValueLabel, totalPnlLabel);
        portTotals.setPadding(new Insets(5, 10, 5, 10));
        portTotals.setStyle("-fx-background-color:#060a00;"
                + "-fx-border-color:" + BD + ";-fx-border-width:1 0 0 0;");

        VBox portPane = new VBox(portForm, portfolioTable, portTotals);
        VBox.setVgrow(portfolioTable, Priority.ALWAYS);
        portPane.setStyle("-fx-background-color:" + BG + ";");

        // ── NEWS ─────────────────────────────────────────────────────────────
        TextField newsTickerField = inlineField("TICKER (IBM works with demo key)", 210);
        Button fetchNewsBtn = retBtn("[FETCH]", TX_B);
        newsStatusLabel = new Label("// IBM WORKS WITH THE DEMO KEY");
        newsStatusLabel.setStyle("-fx-text-fill:" + TX_D + ";-fx-font-size:9px;-fx-font-family:" + FNT + ";");
        fetchNewsBtn.setOnAction(e -> {
            String t = newsTickerField.getText().trim().toUpperCase();
            if (!t.isEmpty()) fetchNews(t);
        });
        newsTickerField.setOnAction(e -> fetchNewsBtn.fire());

        HBox newsForm = new HBox(6, newsTickerField, fetchNewsBtn, newsStatusLabel);
        newsForm.setPadding(new Insets(6, 10, 6, 10));
        newsForm.setAlignment(Pos.CENTER_LEFT);
        newsForm.setStyle("-fx-background-color:" + BG_P + ";"
                + "-fx-border-color:" + BD + ";-fx-border-width:0 0 1 0;");
        newsListView = new ListView<>();
        newsListView.setCellFactory(lv -> new NewsCell());
        newsListView.setPlaceholder(new Label("// NO NEWS LOADED") {{
            setStyle("-fx-text-fill:" + TX_D + ";-fx-font-family:" + FNT + ";"); }});
        VBox.setVgrow(newsListView, Priority.ALWAYS);

        VBox newsPane = new VBox(newsForm, newsListView);
        VBox.setVgrow(newsListView, Priority.ALWAYS);
        newsPane.setStyle("-fx-background-color:" + BG + ";");

        // ── Assemble ─────────────────────────────────────────────────────────
        TabPane tabs = new TabPane(
                new Tab("▸ ALERTS",    alertsPane),
                new Tab("▸ PORTFOLIO", portPane),
                new Tab("▸ NEWS",      newsPane));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color:" + BG + ";");
        return tabs;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FEATURE LOGIC
    // ─────────────────────────────────────────────────────────────────────────

    /** Central entry point for all fetch operations. */
    private void triggerFetch(String ticker) {
        if (ticker == null || ticker.isBlank()) return;
        currentTicker = ticker;
        settings.set("last.ticker", ticker);
        spinner.setVisible(true);
        setStatus("FETCHING " + ticker + "...", false);

        String apiKey = apiKeyField.getText().trim();
        settings.set(SettingsManager.KEY_API_KEY, apiKey);

        new Thread(() -> {
            List<PricePoint> data = api.fetchHistory(ticker, apiKey);
            Platform.runLater(() -> {
                spinner.setVisible(false);
                if (data == null || data.isEmpty()) {
                    String err = api.getLastError().isEmpty() ? "NO DATA FOR " + ticker : api.getLastError();
                    setStatus("ERR // " + err.toUpperCase(), true);
                } else {
                    allData = data;
                    db.savePriceHistory(ticker, data);
                    applyTimeframe(currentDays);
                    checkAlerts(data);
                    setStatus("OK  // " + data.size() + " POINTS FOR " + ticker + " — SAVED.", false);
                }
            });
        }).start();
    }

    private void applyTimeframe(int days) {
        currentDays  = days;
        filteredData = ChartDataHelper.filterByDays(allData, days);
        refreshChart();
        updatePrice(filteredData);
        // Highlight active timeframe button
        List.of(btn1W, btn1M, btn3M, btn1Y, btnAll).forEach(b -> b.setStyle(tfBtnStyle(false)));
        switch (days) {
            case 7   -> btn1W.setStyle(tfBtnStyle(true));
            case 30  -> btn1M.setStyle(tfBtnStyle(true));
            case 90  -> btn3M.setStyle(tfBtnStyle(true));
            case 365 -> btn1Y.setStyle(tfBtnStyle(true));
            default  -> btnAll.setStyle(tfBtnStyle(true));
        }
    }

    private void refreshChart() {
        if (filteredData == null || filteredData.isEmpty()) return;
        chart.getData().clear();

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(currentTicker);
        int step = Math.max(1, filteredData.size() / 150);
        for (int i = 0; i < filteredData.size(); i += step)
            series.getData().add(new XYChart.Data<>(
                    filteredData.get(i).getDate(), filteredData.get(i).getPrice()));
        // Always include the last point
        PricePoint last = filteredData.get(filteredData.size() - 1);
        if (!series.getData().isEmpty() &&
                !series.getData().get(series.getData().size() - 1).getXValue().equals(last.getDate()))
            series.getData().add(new XYChart.Data<>(last.getDate(), last.getPrice()));

        chart.getData().add(series);
        if (showSMA20) chart.getData().add(ChartDataHelper.computeSMA(filteredData, 20, "SMA-20"));
        if (showSMA50) chart.getData().add(ChartDataHelper.computeSMA(filteredData, 50, "SMA-50"));
        if (showEMA20) chart.getData().add(ChartDataHelper.computeEMA(filteredData, 20, "EMA-20"));
        chart.setTitle("// " + currentTicker + " — PRICE HISTORY");
    }

    private void updatePrice(List<PricePoint> data) {
        if (data == null || data.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        priceLabel.setText(String.format("$%.2f", latest));
        if (previousPrice > 0) {
            double d   = latest - previousPrice;
            double pct = (d / previousPrice) * 100;
            String sgn = d >= 0 ? "+" : "";
            priceChangeLabel.setText(String.format("%s$%.2f%n(%s%.2f%%)", sgn, d, sgn, pct));
            priceChangeLabel.setStyle("-fx-text-fill:" + (d >= 0 ? TX_B : TX_R)
                    + ";-fx-font-size:10px;-fx-font-family:" + FNT + ";");
        }
        previousPrice = latest;
    }

    private void addAlert() {
        if (currentTicker.isEmpty()) { setStatus("ERR // LOAD AN ASSET FIRST.", true); return; }
        try {
            double target = Double.parseDouble(alertTargetField.getText().trim());
            if (target <= 0) throw new NumberFormatException();
            db.addAlert(currentTicker, target);
            alertTargetField.clear();
            refreshAlertsList();
            setStatus(String.format("OK  // ALERT: %s >= $%.2f", currentTicker, target), false);
        } catch (NumberFormatException ex) {
            setStatus("ERR // ENTER A VALID POSITIVE PRICE.", true);
        }
    }

    private void deleteAlert() {
        Alert sel = alertsListView.getSelectionModel().getSelectedItem();
        if (sel == null) { setStatus("ERR // SELECT AN ALERT.", true); return; }
        db.deleteAlert(sel.getId());
        refreshAlertsList();
        setStatus("OK  // ALERT DELETED.", false);
    }

    /**
     * UPDATE — opens a dialog to change the target price of an existing alert.
     * Calls {@link DatabaseManager#updateAlertPrice(int, double)} which executes
     * an explicit {@code UPDATE alerts SET target_price = ? WHERE id = ?} statement.
     */
    private void editAlert() {
        Alert sel = alertsListView.getSelectionModel().getSelectedItem();
        if (sel == null) { setStatus("ERR // SELECT AN ALERT TO EDIT.", true); return; }

        TextInputDialog dialog = new TextInputDialog(String.format("%.2f", sel.getTargetPrice()));
        dialog.setTitle("EDIT ALERT");
        dialog.setHeaderText("UPDATE TARGET PRICE FOR " + sel.getTicker());
        dialog.setContentText("NEW PRICE $:");
        // Apply retro styling
        dialog.getDialogPane().setStyle("-fx-background-color:#080808;-fx-font-family:'Courier New';");
        dialog.getDialogPane().getChildren().forEach(n ->
                n.setStyle("-fx-text-fill:#00cc33;-fx-font-family:'Courier New';"));

        dialog.showAndWait().ifPresent(input -> {
            try {
                double newPrice = Double.parseDouble(input.trim());
                if (newPrice <= 0) throw new NumberFormatException();
                db.updateAlertPrice(sel.getId(), newPrice);   // ← explicit SQL UPDATE
                refreshAlertsList();
                setStatus(String.format("OK  // ALERT UPDATED: %s @ $%.2f", sel.getTicker(), newPrice), false);
            } catch (NumberFormatException ex) {
                setStatus("ERR // INVALID PRICE — ENTER A POSITIVE NUMBER.", true);
            }
        });
    }


    private void checkAlerts(List<PricePoint> data) {
        if (data == null || data.isEmpty()) return;
        double latest = data.get(data.size() - 1).getPrice();
        for (Alert a : db.getAllAlerts()) {
            if (!a.getTicker().equalsIgnoreCase(currentTicker)) continue;
            if (latest >= a.getTargetPrice()) {
                String msg = String.format("%s HIT $%.2f  (TARGET $%.2f)",
                        currentTicker, latest, a.getTargetPrice());
                setStatus(">> ALERT: " + msg, false);
                NotificationService.show("PRICE ALERT", msg);
            }
        }
        refreshAlertsList();
    }

    private void addPortfolioHolding() {
        String ticker = portTickerF.getText().trim().toUpperCase();
        if (ticker.isEmpty()) { setStatus("ERR // ENTER A TICKER.", true); return; }
        try {
            double qty = Double.parseDouble(portQtyF.getText().trim());
            double avg = Double.parseDouble(portBuyF.getText().trim());
            if (qty <= 0 || avg <= 0) throw new NumberFormatException();
            db.saveHolding(ticker, qty, avg);
            portTickerF.clear(); portQtyF.clear(); portBuyF.clear();
            refreshPortfolioTable();
            setStatus(String.format("OK  // SAVED: %.4f %s @ $%.2f", qty, ticker, avg), false);
        } catch (NumberFormatException ex) {
            setStatus("ERR // ENTER VALID POSITIVE NUMBERS.", true);
        }
    }

    private void refreshPortfolioTable() {
        List<PortfolioHolding> holdings = db.getAllHoldings();
        portfolioRows.clear();
        double totalVal = 0, totalCost = 0;
        for (PortfolioHolding h : holdings) {
            double cur = db.getLatestPrice(h.getTicker());
            PortfolioRow row = new PortfolioRow(h, cur);
            portfolioRows.add(row);
            if (cur > 0) totalVal += row.currentValue;
            totalCost += h.getCostBasis();
        }
        double pnl  = totalVal - totalCost;
        double pct  = totalCost > 0 ? (pnl / totalCost) * 100 : 0;
        String sign = pnl >= 0 ? "+" : "";
        totalValueLabel.setText(String.format("VALUE: $%,.2f", totalVal));
        totalPnlLabel.setText(String.format("P&L: %s$%,.2f (%s%.2f%%)", sign, pnl, sign, pct));
        totalPnlLabel.setStyle("-fx-text-fill:" + (pnl >= 0 ? TX_B : TX_R)
                + ";-fx-font-size:11px;-fx-font-family:" + FNT + ";");
    }

    private void fetchNews(String ticker) {
        newsListView.getItems().clear();
        newsStatusLabel.setText("// FETCHING " + ticker + "...");
        String key = apiKeyField.getText().trim();
        new Thread(() -> {
            List<NewsItem> items = newsFetcher.fetchNews(ticker, key);
            Platform.runLater(() -> {
                if (items.isEmpty())
                    newsStatusLabel.setText("ERR // " + newsFetcher.getLastError().toUpperCase());
                else {
                    newsListView.getItems().setAll(items);
                    newsStatusLabel.setText("OK  // " + items.size() + " ARTICLES FOR " + ticker);
                }
            });
        }).start();
    }

    private void refreshWatchlistView() { watchlistView.getItems().setAll(db.getWatchlist()); }
    private void refreshAlertsList()    { alertsListView.getItems().setAll(db.getAllAlerts()); }

    private void setStatus(String msg, boolean err) {
        statusLabel.setText(msg);
        statusLabel.setStyle("-fx-text-fill:" + (err ? TX_R : TX_M)
                + ";-fx-font-size:10px;-fx-font-family:" + FNT + ";");
    }
    private void setStatus(String msg) { setStatus(msg, false); }

    // ─────────────────────────────────────────────────────────────────────────
    // STYLE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Label panelLabel(String t) {
        Label l = new Label("■ " + t);
        l.setStyle("-fx-text-fill:" + TX_B + ";-fx-font-size:9px;-fx-font-weight:bold;"
                + "-fx-font-family:" + FNT + ";-fx-padding:4 0 1 0;");
        return l;
    }

    private Label subLabel(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill:" + TX_D + ";-fx-font-size:8px;-fx-font-family:" + FNT + ";");
        return l;
    }

    private Button retBtn(String text, String color) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + BG_IN + ";-fx-text-fill:" + color + ";"
                + "-fx-background-radius:0;-fx-border-color:" + BD + ";"
                + "-fx-border-radius:0;-fx-font-size:10px;-fx-font-family:" + FNT + ";-fx-cursor:hand;");
        return b;
    }

    private Button stripBtn(String text) {
        String style = "-fx-background-color:transparent;-fx-text-fill:" + TX_D + ";"
                + "-fx-font-size:10px;-fx-font-family:" + FNT + ";-fx-cursor:hand;-fx-border-color:transparent;";
        String hover  = "-fx-background-color:transparent;-fx-text-fill:" + TX_M + ";"
                + "-fx-font-size:10px;-fx-font-family:" + FNT + ";-fx-cursor:hand;-fx-border-color:transparent;";
        Button b = new Button("▸ " + text);
        b.setStyle(style);
        b.setOnMouseEntered(e -> b.setStyle(hover));
        b.setOnMouseExited(e  -> b.setStyle(style));
        return b;
    }

    private Button tfBtn(String text) { Button b = new Button(text); b.setStyle(tfBtnStyle(false)); return b; }

    private String tfBtnStyle(boolean active) {
        return active
                ? "-fx-background-color:#001a00;-fx-text-fill:" + TX_B + ";-fx-font-weight:bold;"
                  + "-fx-background-radius:0;-fx-border-color:" + TX_M + ";-fx-border-radius:0;"
                  + "-fx-font-size:10px;-fx-font-family:" + FNT + ";-fx-cursor:hand;"
                : "-fx-background-color:" + BG_IN + ";-fx-text-fill:" + TX_D + ";"
                  + "-fx-background-radius:0;-fx-border-color:" + BD + ";-fx-border-radius:0;"
                  + "-fx-font-size:10px;-fx-font-family:" + FNT + ";-fx-cursor:hand;";
    }

    private CheckBox chk(String text, String color) {
        CheckBox cb = new CheckBox(text);
        cb.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;-fx-font-family:" + FNT + ";");
        return cb;
    }

    private Separator vSep() {
        Separator s = new Separator(Orientation.VERTICAL);
        s.setStyle("-fx-background-color:" + BD + ";");
        return s;
    }

    private Separator hLine() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:" + BD + ";");
        VBox.setMargin(s, new Insets(4, 0, 4, 0));
        return s;
    }

    private TextField inlineField(String prompt, double width) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(width);
        tf.setStyle("-fx-background-color:" + BG_IN + ";-fx-text-fill:" + TX + ";"
                + "-fx-prompt-text-fill:" + TX_D + ";-fx-background-radius:0;"
                + "-fx-border-color:" + BD + ";-fx-border-radius:0;"
                + "-fx-font-size:10px;-fx-font-family:" + FNT + ";");
        return tf;
    }

    private TableColumn<PortfolioRow, String> col(String header, String field) {
        TableColumn<PortfolioRow, String> c = new TableColumn<>(header);
        c.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(field)));
        return c;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INNER CLASSES
    // ─────────────────────────────────────────────────────────────────────────

    static class PortfolioRow {
        final PortfolioHolding holding;
        final double currentPrice, currentValue, pnl, pnlPct;
        PortfolioRow(PortfolioHolding h, double cur) {
            holding = h; currentPrice = cur;
            currentValue = cur > 0 ? h.getQuantity() * cur : 0;
            pnl    = currentValue > 0 ? currentValue - h.getCostBasis() : 0;
            pnlPct = h.getCostBasis() > 0 ? (pnl / h.getCostBasis()) * 100 : 0;
        }
        String get(String f) {
            String s = pnl >= 0 ? "+" : "";
            return switch (f) {
                case "ticker"  -> holding.getTicker();
                case "qty"     -> String.format("%.4f", holding.getQuantity());
                case "avgBuy"  -> String.format("$%.2f", holding.getAvgBuyPrice());
                case "current" -> currentPrice > 0 ? String.format("$%.2f", currentPrice) : "N/A";
                case "value"   -> currentValue > 0 ? String.format("$%,.2f", currentValue) : "N/A";
                case "pnl"     -> currentValue > 0 ? String.format("%s$%,.2f", s, pnl) : "N/A";
                case "pnlPct"  -> currentValue > 0 ? String.format("%s%.2f%%", s, pnlPct) : "N/A";
                default        -> "";
            };
        }
    }

    /** Dropdown list cell — shows group headers as dim, non-selectable separators. */
    private static class AssetListCell extends ListCell<String> {
        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setStyle("-fx-background-color:#060a00;"); return; }
            if (item.startsWith("──")) {
                setText(item);
                setDisable(true);
                setStyle("-fx-background-color:#0a0f00;-fx-text-fill:#003300;"
                        + "-fx-font-size:9px;-fx-font-family:'Courier New';-fx-padding:3 6 3 6;");
            } else {
                setText(">  " + item);
                setDisable(false);
                setStyle("-fx-background-color:#060a00;-fx-text-fill:#00cc33;"
                        + "-fx-font-size:11px;-fx-font-family:'Courier New';");
            }
        }
    }

    /** Button cell (closed ComboBox display) — just shows the ticker in bright green. */
    private static class AssetButtonCell extends ListCell<String> {
        @Override protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText((empty || item == null || item.startsWith("──")) ? "" : item);
            setStyle("-fx-background-color:#000d00;-fx-text-fill:#00ff41;"
                    + "-fx-font-size:12px;-fx-font-weight:bold;-fx-font-family:'Courier New';");
        }
    }

    private class AlertCell extends ListCell<Alert> {
        @Override protected void updateItem(Alert a, boolean empty) {
            super.updateItem(a, empty);
            if (empty || a == null) { setText(null); setStyle("-fx-background-color:#060a00;"); return; }
            setText(a.toString());
            boolean hit = previousPrice > 0 && previousPrice >= a.getTargetPrice()
                    && a.getTicker().equalsIgnoreCase(currentTicker);
            setStyle(hit
                    ? "-fx-background-color:#1a0a00;-fx-text-fill:" + TX_A + ";-fx-font-family:'Courier New';-fx-font-size:11px;"
                    : "-fx-background-color:#060a00;-fx-text-fill:" + TX + ";-fx-font-family:'Courier New';-fx-font-size:11px;");
        }
    }

    private static class WatchlistCell extends ListCell<WatchlistItem> {
        @Override protected void updateItem(WatchlistItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setStyle("-fx-background-color:#060a00;"); return; }
            setText(">  " + item.getTicker());
            setStyle("-fx-background-color:#060a00;-fx-text-fill:#00cc33;"
                    + "-fx-font-family:'Courier New';-fx-font-size:11px;");
        }
    }

    private static class NewsCell extends ListCell<NewsItem> {
        @Override protected void updateItem(NewsItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null); setStyle("-fx-background-color:#060a00;"); return; }

            String badge = switch (item.getSentimentLabel().toLowerCase()) {
                case "bullish", "somewhat bullish" -> "[BULL]";
                case "bearish", "somewhat bearish" -> "[BEAR]";
                default                            -> "[NEUT]";
            };
            String badgeColor = switch (item.getSentimentLabel().toLowerCase()) {
                case "bullish", "somewhat bullish" -> "#00ff41";
                case "bearish", "somewhat bearish" -> "#ff3300";
                default                            -> "#009922";
            };

            VBox box = new VBox(2);
            box.setPadding(new Insets(5, 8, 5, 8));
            Label title = new Label(item.getTitle());
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            title.setStyle("-fx-text-fill:#00cc33;-fx-font-size:11px;-fx-font-weight:bold;-fx-font-family:'Courier New';");
            Label meta = new Label(badge + "  " + item.getSource() + "  //  " + item.getFormattedDate());
            meta.setStyle("-fx-text-fill:" + badgeColor + ";-fx-font-size:9px;-fx-font-family:'Courier New';");
            box.getChildren().addAll(title, meta);
            box.setStyle("-fx-background-color:#060a00;-fx-border-color:#002a00;-fx-border-width:0 0 1 0;");

            setText(null);
            setGraphic(box);
            setStyle("-fx-background-color:#060a00;");
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !item.getUrl().isEmpty())
                    try { Desktop.getDesktop().browse(new URI(item.getUrl())); }
                    catch (Exception ex) { System.err.println("[News] " + ex.getMessage()); }
            });
        }
    }
}
