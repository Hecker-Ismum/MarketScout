package com.app;

// ──────────────────────────────────────────────
// JavaFX core imports
// ──────────────────────────────────────────────
import javafx.application.Application; // Base class for all JavaFX apps
import javafx.stage.Stage; // The top-level window (frame)
import javafx.scene.Scene; // Container that holds the scene graph

// ──────────────────────────────────────────────
// Layout imports
// ──────────────────────────────────────────────
import javafx.scene.layout.BorderPane; // 5-region layout: top, bottom, left, right, center
import javafx.scene.layout.VBox; // Vertical box layout (stacks children top-to-bottom)

// ──────────────────────────────────────────────
// UI control imports
// ──────────────────────────────────────────────
import javafx.scene.control.TextField; // Single-line text input field
import javafx.scene.control.Button; // Clickable button
import javafx.scene.control.Label; // Non-editable text label

// ──────────────────────────────────────────────
// Chart imports
// ──────────────────────────────────────────────
import javafx.scene.chart.LineChart; // Draws data as connected line segments
import javafx.scene.chart.NumberAxis; // Numeric axis for the chart
import javafx.scene.chart.CategoryAxis; // String-based axis (used here for dates)
import javafx.scene.chart.XYChart; // Provides Series and Data for populating charts

// ──────────────────────────────────────────────
// Geometry / spacing
// ──────────────────────────────────────────────
import javafx.geometry.Insets; // Padding / margin values
import javafx.geometry.Pos; // Alignment constants (CENTER, TOP_LEFT, etc.)

/**
 * MarketScout — a JavaFX desktop application for visualising
 * stock and cryptocurrency price data on a line chart.
 *
 * Layout overview (BorderPane):
 * ┌────────────────────────────────────────────┐
 * │ (top) │
 * ├──────────┬─────────────────────────────────┤
 * │ LEFT │ CENTER │
 * │ VBox │ LineChart │
 * │ ┌────┐ │ (Date vs Price) │
 * │ │Text│ │ │
 * │ │Field│ │ │
 * │ ├────┤ │ │
 * │ │Btn │ │ │
 * │ └────┘ │ │
 * ├──────────┴─────────────────────────────────┤
 * │ (bottom) │
 * └────────────────────────────────────────────┘
 */
public class Main extends Application {

  // ──────────────────────────────────────────
  // Class-level references so event handlers
  // can access these controls from any method.
  // ──────────────────────────────────────────
  private TextField tickerInput;
  private LineChart<String, Number> priceChart;

  /**
   * Entry point — called by the JVM.
   * Application.launch() bootstraps the JavaFX runtime
   * and eventually calls start(Stage).
   */
  public static void main(String[] args) {
    launch(args);
  }

  /**
   * Called by the JavaFX framework after initialisation.
   * This is where we build the entire UI scene graph.
   *
   * @param primaryStage the main application window provided by JavaFX
   */
  @Override
  public void start(Stage primaryStage) {

    // =============================================
    // 1. ROOT LAYOUT — BorderPane
    // =============================================
    // BorderPane divides the window into five regions:
    // top, bottom, left, right, and center.
    BorderPane root = new BorderPane();

    // =============================================
    // 2. LEFT SIDEBAR — VBox with input controls
    // =============================================
    // VBox stacks its children vertically with 10px spacing.
    VBox sidebar = new VBox(10);
    sidebar.setPadding(new Insets(15)); // 15px padding on all sides
    sidebar.setAlignment(Pos.TOP_CENTER); // Centre children horizontally
    sidebar.setPrefWidth(200); // Fixed sidebar width
    sidebar.setStyle(
        "-fx-background-color: #1e1e2f;" // Dark background
    );

    // --- Sidebar title label ---
    Label titleLabel = new Label("MarketScout");
    titleLabel.setStyle(
        "-fx-font-size: 18px;"
            + "-fx-font-weight: bold;"
            + "-fx-text-fill: #e0e0e0;" // Light text on dark bg
    );

    // --- Ticker input field ---
    // The user types a stock or crypto symbol here (e.g. AAPL, BTC).
    tickerInput = new TextField();
    tickerInput.setPromptText("Enter ticker (e.g. AAPL)"); // Placeholder text
    tickerInput.setMaxWidth(170);
    tickerInput.setStyle(
        "-fx-background-color: #2a2a40;"
            + "-fx-text-fill: #ffffff;"
            + "-fx-prompt-text-fill: #888888;"
            + "-fx-background-radius: 5;");

    // --- Fetch button ---
    // Clicking this will load sample data into the chart.
    Button fetchButton = new Button("Fetch Data");
    fetchButton.setMaxWidth(170);
    fetchButton.setStyle(
        "-fx-background-color: #4a90d9;" // Blue accent
            + "-fx-text-fill: white;"
            + "-fx-font-weight: bold;"
            + "-fx-background-radius: 5;"
            + "-fx-cursor: hand;");

    // Attach an event handler: when the button is clicked,
    // call loadSampleData() with whatever ticker the user typed.
    fetchButton.setOnAction(event -> loadSampleData(tickerInput.getText()));

    // --- Status label (feedback to the user) ---
    Label statusLabel = new Label("Enter a ticker and click Fetch.");
    statusLabel.setWrapText(true);
    statusLabel.setStyle(
        "-fx-text-fill: #aaaaaa;"
            + "-fx-font-size: 11px;");

    // Add all sidebar children in top-to-bottom order.
    sidebar.getChildren().addAll(titleLabel, tickerInput, fetchButton, statusLabel);

    // Place the sidebar on the LEFT region of the BorderPane.
    root.setLeft(sidebar);

    // =============================================
    // 3. CENTER — LineChart (Date vs. Price)
    // =============================================

    // --- X-axis: dates shown as category strings ---
    CategoryAxis xAxis = new CategoryAxis();
    xAxis.setLabel("Date");
    xAxis.setStyle("-fx-tick-label-fill: #cccccc;");

    // --- Y-axis: price as a number ---
    NumberAxis yAxis = new NumberAxis();
    yAxis.setLabel("Price (USD)");
    yAxis.setStyle("-fx-tick-label-fill: #cccccc;");

    // Create the LineChart using both axes.
    // Generic types: <String, Number> because X = date strings, Y = numbers.
    priceChart = new LineChart<>(xAxis, yAxis);
    priceChart.setTitle("Price History");
    priceChart.setAnimated(true); // Smooth transitions when data changes
    priceChart.setCreateSymbols(true); // Show data-point dots on the line
    priceChart.setStyle(
        "-fx-background-color: #14141f;" // Dark chart background
    );

    // Place the chart in the CENTER region (fills remaining space).
    root.setCenter(priceChart);

    // =============================================
    // 4. SCENE & STAGE setup
    // =============================================

    // A Scene holds the entire UI tree; set its size to 960 × 600.
    Scene scene = new Scene(root, 960, 600);

    // Apply a dark background to the whole scene.
    scene.setFill(javafx.scene.paint.Color.web("#14141f"));

    // Configure the primary stage (window).
    primaryStage.setTitle("MarketScout — Market Data Viewer");
    primaryStage.setScene(scene);
    primaryStage.setMinWidth(700); // Prevent the window from getting too small
    primaryStage.setMinHeight(450);
    primaryStage.show(); // Display the window
  }

  // ─────────────────────────────────────────────────
  // HELPER: Populate the chart with sample data
  // ─────────────────────────────────────────────────

  /**
   * Loads sample price data into the LineChart for the given ticker symbol.
   *
   * In a real application you would replace this method with an HTTP call
   * to a market-data API (e.g. Alpha Vantage, CoinGecko) and parse the
   * JSON response with Gson. For now, hard-coded data demonstrates the
   * chart functionality.
   *
   * @param ticker the stock or crypto symbol entered by the user
   */
  private void loadSampleData(String ticker) {
    // Clear any previously displayed series.
    priceChart.getData().clear();

    // Use a default ticker if the input is empty.
    String symbol = (ticker == null || ticker.isBlank()) ? "AAPL" : ticker.toUpperCase();

    // Update the chart title to reflect the selected ticker.
    priceChart.setTitle(symbol + " — Price History");

    // Create a new data series (one line on the chart).
    XYChart.Series<String, Number> series = new XYChart.Series<>();
    series.setName(symbol + " Price");

    // ── Sample data points (Date → Price) ──
    // Each XYChart.Data object is one point on the line.
    series.getData().add(new XYChart.Data<>("2024-01-01", 185.50));
    series.getData().add(new XYChart.Data<>("2024-02-01", 190.25));
    series.getData().add(new XYChart.Data<>("2024-03-01", 178.80));
    series.getData().add(new XYChart.Data<>("2024-04-01", 195.40));
    series.getData().add(new XYChart.Data<>("2024-05-01", 210.15));
    series.getData().add(new XYChart.Data<>("2024-06-01", 205.70));
    series.getData().add(new XYChart.Data<>("2024-07-01", 220.30));
    series.getData().add(new XYChart.Data<>("2024-08-01", 215.90));
    series.getData().add(new XYChart.Data<>("2024-09-01", 230.50));
    series.getData().add(new XYChart.Data<>("2024-10-01", 225.10));
    series.getData().add(new XYChart.Data<>("2024-11-01", 240.00));
    series.getData().add(new XYChart.Data<>("2024-12-01", 235.60));

    // Add the series to the chart (you can add multiple series).
    priceChart.getData().add(series);
  }
}
