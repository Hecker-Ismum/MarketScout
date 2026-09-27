package com.app;

import javafx.scene.chart.XYChart;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-computation helpers for chart data:
 * <ul>
 *   <li>Time-frame filtering</li>
 *   <li>Simple Moving Average (SMA)</li>
 *   <li>Exponential Moving Average (EMA)</li>
 * </ul>
 * All methods are stateless and thread-safe.
 */
public final class ChartDataHelper {

    private ChartDataHelper() {} // utility class

    // ── Timeframe filtering ───────────────────────────────────────────────────

    /**
     * Returns the last {@code days} items from {@code data}, or the full list
     * if {@code days} ≤ 0 or greater than the list size.
     */
    public static List<PricePoint> filterByDays(List<PricePoint> data, int days) {
        if (data == null || data.isEmpty() || days <= 0 || days >= data.size()) return data;
        return new ArrayList<>(data.subList(data.size() - days, data.size()));
    }

    // ── Simple Moving Average ─────────────────────────────────────────────────

    /**
     * Computes an SMA series over the given price data.
     *
     * @param data   source price points (must be sorted by date ascending)
     * @param period look-back window (e.g. 20 for SMA-20)
     * @param name   series name shown in the chart legend
     * @return an {@link XYChart.Series} ready to add to a JavaFX LineChart
     */
    public static XYChart.Series<String, Number> computeSMA(
            List<PricePoint> data, int period, String name) {

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);

        if (data == null || data.size() < period) return series;

        // Pre-sum the first window
        double windowSum = 0;
        for (int i = 0; i < period; i++) windowSum += data.get(i).getPrice();

        // First SMA value
        series.getData().add(new XYChart.Data<>(
                data.get(period - 1).getDate(), windowSum / period));

        // Slide the window across remaining points
        for (int i = period; i < data.size(); i++) {
            windowSum += data.get(i).getPrice();
            windowSum -= data.get(i - period).getPrice();
            series.getData().add(new XYChart.Data<>(
                    data.get(i).getDate(), windowSum / period));
        }

        return series;
    }

    // ── Exponential Moving Average ────────────────────────────────────────────

    /**
     * Computes an EMA series over the given price data.
     *
     * @param data   source price points (must be sorted by date ascending)
     * @param period EMA period (e.g. 20 for EMA-20)
     * @param name   series name shown in the chart legend
     * @return an {@link XYChart.Series} ready to add to a JavaFX LineChart
     */
    public static XYChart.Series<String, Number> computeEMA(
            List<PricePoint> data, int period, String name) {

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(name);

        if (data == null || data.size() < period) return series;

        double multiplier = 2.0 / (period + 1);

        // Seed EMA as the simple average of the first `period` values
        double ema = 0;
        for (int i = 0; i < period; i++) ema += data.get(i).getPrice();
        ema /= period;
        series.getData().add(new XYChart.Data<>(data.get(period - 1).getDate(), ema));

        // Apply EMA formula for remaining points
        for (int i = period; i < data.size(); i++) {
            ema = (data.get(i).getPrice() - ema) * multiplier + ema;
            series.getData().add(new XYChart.Data<>(data.get(i).getDate(), ema));
        }

        return series;
    }
}
