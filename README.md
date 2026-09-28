# MarketScout

MarketScout is a comprehensive JavaFX application designed for tracking and managing stock and cryptocurrency prices.

## Features

- **Real-Time Data Tracking**: Fetch the latest prices using live APIs via `ApiFetcher`.
- **Price History and Charting**: Visualize market trends and historical data with intuitive charts powered by `ChartDataHelper`.
- **Scheduled Updates**: Automatically poll market prices at regular intervals using `PriceScheduler`.
- **Data Persistence**: Store price history efficiently in a local SQLite database (`DatabaseManager`).
- **News Integration**: Stay up to date with the latest financial news via `NewsItem` tracking.
- **Data Export**: Export your market data to CSV formats for external analysis (`CsvExporter`).

## Tech Stack

- **Java 17**
- **JavaFX 21**
- **SQLite** for data storage
- **Gson** for JSON parsing
- **Maven** for build and dependency management

## How to Run

1. Ensure you have Java 17+ installed.
2. Build and run using Maven:
   ```bash
   mvn clean javafx:run
   ```

