package com.app;

import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.util.List;

/**
 * Exports a list of {@link PricePoint} objects to a CSV file chosen by the user
 * via a native save dialog.
 *
 * <p>CSV columns: {@code Date, Ticker, Close, Open, High, Low}</p>
 */
public final class CsvExporter {

    private CsvExporter() {}

    /**
     * Opens a save-file dialog and writes the price data to the chosen file.
     * Must be called on the JavaFX Application Thread (because of FileChooser).
     *
     * @param ticker      the asset symbol used for the default file name
     * @param data        price points to export
     * @param ownerStage  parent window for the native dialog
     */
    public static void export(String ticker, List<PricePoint> data, Stage ownerStage) {
        if (data == null || data.isEmpty()) {
            System.err.println("[CSV] Nothing to export.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Price History as CSV");
        chooser.setInitialFileName(ticker + "_history.csv");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );

        File file = chooser.showSaveDialog(ownerStage);
        if (file == null) return; // user cancelled

        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
            pw.println("Date,Ticker,Close,Open,High,Low");
            for (PricePoint p : data) {
                pw.printf("%s,%s,%.6f,%.6f,%.6f,%.6f%n",
                        p.getDate(),
                        p.getSymbol(),
                        p.getPrice(),
                        p.getOpen(),
                        p.getHigh(),
                        p.getLow());
            }
            System.out.println("[CSV] Exported " + data.size() + " rows to " + file.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[CSV] Export failed: " + e.getMessage());
        }
    }
}
