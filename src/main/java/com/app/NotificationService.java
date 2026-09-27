package com.app;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Displays a transient toast-style popup in the bottom-right corner of the
 * primary screen. Appears for ~4 seconds then fades out automatically.
 *
 * <p>All calls are safe from any thread — internally routes to the JavaFX
 * Application Thread via {@link Platform#runLater(Runnable)}.</p>
 */
public final class NotificationService {

    private NotificationService() {}

    /**
     * Shows a toast notification.
     *
     * @param title   short heading (e.g. "Price Alert Triggered")
     * @param message detail line (e.g. "AAPL hit $220.00 (target $200.00)")
     */
    public static void show(String title, String message) {
        Platform.runLater(() -> {
            Stage toast = new Stage(StageStyle.TRANSPARENT);
            toast.setAlwaysOnTop(true);
            toast.setResizable(false);

            // ── Layout ───────────────────────────────────────────────────────
            VBox box = new VBox(5);
            box.setPadding(new Insets(14, 18, 14, 18));
            box.setMaxWidth(320);
            box.setStyle(
                    "-fx-background-color: #1e2a4a;"
                    + "-fx-border-color: #3a7bd5;"
                    + "-fx-border-width: 1;"
                    + "-fx-border-radius: 8;"
                    + "-fx-background-radius: 8;"
            );

            Label titleLabel = new Label("🔔 " + title);
            titleLabel.setStyle(
                    "-fx-text-fill: #7dd3fc; -fx-font-weight: bold; -fx-font-size: 13px;");
            titleLabel.setWrapText(true);

            Label msgLabel = new Label(message);
            msgLabel.setStyle("-fx-text-fill: #c0c0e0; -fx-font-size: 11px;");
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(280);

            box.getChildren().addAll(titleLabel, msgLabel);
            box.setAlignment(Pos.CENTER_LEFT);

            // ── Scene ────────────────────────────────────────────────────────
            Scene scene = new Scene(box);
            scene.setFill(Color.TRANSPARENT);
            toast.setScene(scene);

            // ── Position: bottom-right ───────────────────────────────────────
            Rectangle2D screen = Screen.getPrimary().getVisualBounds();
            toast.show();                              // show first so we know its size
            toast.setX(screen.getMaxX() - toast.getWidth() - 20);
            toast.setY(screen.getMaxY() - toast.getHeight() - 20);

            // ── Fade-out after 3.5 s ─────────────────────────────────────────
            FadeTransition fade = new FadeTransition(Duration.seconds(1.2), box);
            fade.setDelay(Duration.seconds(3.5));
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> toast.close());
            fade.play();
        });
    }
}
