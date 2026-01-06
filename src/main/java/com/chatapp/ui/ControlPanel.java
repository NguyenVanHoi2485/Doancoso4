package com.chatapp.ui;

import com.chatapp.server.ChatServer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class ControlPanel extends HBox {
    private final Button startButton;
    private final Button stopButton;
    private final Label statusLabel;
    private final Label clientCountLabel;
    private final ChatServer chatServer;

    public ControlPanel(ChatServer chatServer) {
        this.chatServer = chatServer;
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(15);
        setPadding(new Insets(15));

        // CSS Class cho Panel chính
        getStyleClass().add("panel-box");
        // Ghi đè padding/màu nền một chút cho thanh trên cùng
        setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #ecf0f1 transparent; -fx-border-width: 2;");

        // === NÚT ===
        startButton = new Button("▶ Start Server");
        startButton.getStyleClass().addAll("button", "start-button"); // Class CSS
        startButton.setPrefWidth(130);

        // --- SỬA LỖI TẠI ĐÂY ---
        // Thay vì gọi chatServer.startServer(), ta gọi qua getNetworkManager()
        startButton.setOnAction(e -> {
            if (chatServer.getNetworkManager() != null) {
                chatServer.getNetworkManager().startServer();
            }
        });

        stopButton = new Button("⏹ Stop Server");
        stopButton.getStyleClass().addAll("button", "stop-button"); // Class CSS
        stopButton.setPrefWidth(130);
        stopButton.setDisable(true);
        stopButton.setOnAction(e -> chatServer.stopServer());

        // === NHÃN ===
        statusLabel = new Label("Status: Stopped");
        statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #7f8c8d;");

        clientCountLabel = new Label("Clients: 0");
        clientCountLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #3498db; -fx-background-color: #ebf5fb; -fx-padding: 5 10; -fx-background-radius: 10;");

        // === SPACER ===
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // === THÊM VÀO HBOX ===
        getChildren().addAll(
                startButton,
                stopButton,
                spacer,
                statusLabel,
                clientCountLabel
        );
    }

    public void setRunning(boolean running) {
        startButton.setDisable(running);
        stopButton.setDisable(!running);
        if (running) {
            statusLabel.setText("Status: ● Running (Port 5555)");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2ecc71;"); // Màu xanh
        } else {
            statusLabel.setText("Status: ● Stopped");
            statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e74c3c;"); // Màu đỏ
        }
    }

    public void updateClientCount(int count) {
        clientCountLabel.setText("Clients: " + count);
    }
}