package com.chatapp.server;

import com.chatapp.ui.ChatPanel;
import com.chatapp.ui.ControlPanel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ChatServer extends Application {

    // Components
    private BorderPane mainLayout;
    private TextArea logArea;
    private Label statusLabel; // Footer Status

    // References to Panels
    private ClientsPanel clientsPanelRef;
    private GroupsPanel groupsPanelRef;

    // Logic
    private ServerNetworkManager networkManager;
    private ChatServerController controller;
    private TextField broadcastField;

    @Override
    public void start(Stage stage) {
        networkManager = new ServerNetworkManager(this);
        controller = new ChatServerController(this, networkManager);

        // 1. Root Layout
        mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(10));

        // 2. TOP: Control Panel (Start/Stop)
        ControlPanel controlPanel = new ControlPanel(this);
        mainLayout.setTop(controlPanel);

        // 3. CENTER: SplitPane (Left: Tables, Right: Logs)
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.4); // 40% trái, 60% phải
        BorderPane.setMargin(splitPane, new Insets(10, 0, 10, 0));

        // --- LEFT SIDE: Tables (Clients & Groups) ---
        clientsPanelRef = new ClientsPanel(this);
        groupsPanelRef = new GroupsPanel(this);

        VBox leftPane = new VBox(10);
        leftPane.getChildren().addAll(clientsPanelRef, groupsPanelRef);
        // Cho 2 bảng giãn đều nhau
        VBox.setVgrow(clientsPanelRef, Priority.ALWAYS);
        VBox.setVgrow(groupsPanelRef, Priority.ALWAYS);

        // --- RIGHT SIDE: System Logs ---
        VBox rightPane = new VBox(5);
        Label logTitle = new Label("📝 System Logs");
        logTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.getStyleClass().add("log-console"); // CSS style
        logArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-control-inner-background: #1e1e1e; -fx-text-fill: #00ff00;");
        VBox.setVgrow(logArea, Priority.ALWAYS);

        HBox broadcastBox = new HBox(10);
        broadcastBox.setPadding(new Insets(5, 0, 0, 0));

        broadcastField = new TextField();
        broadcastField.setPromptText("Nhập thông báo toàn hệ thống...");
        HBox.setHgrow(broadcastField, Priority.ALWAYS);

        Button sendBtn = new Button("📢 Send");
        sendBtn.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");

        sendBtn.setOnAction(e -> sendBroadcast());
        broadcastField.setOnAction(e -> sendBroadcast()); // Enter cũng gửi luôn

        broadcastBox.getChildren().addAll(broadcastField, sendBtn);

        rightPane.getChildren().addAll(logTitle, logArea, broadcastBox);

        splitPane.getItems().addAll(leftPane, rightPane);
        mainLayout.setCenter(splitPane);

        HBox statusBar = new HBox(10);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-border-color: #ccc; -fx-border-width: 1 0 0 0;");
        statusLabel = new Label("Ready to start...");
        statusBar.getChildren().add(statusLabel);
        mainLayout.setBottom(statusBar);

        Scene scene = new Scene(mainLayout, 1000, 650);
        try {
            scene.getStylesheets().add(getClass().getResource("/server-style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("Warning: server-style.css not found.");
        }

        stage.setTitle("Chat Server Console (Unified UI)");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> stopServer());
        stage.show();

        Platform.runLater(() -> {
            networkManager.startServer();
            FileTransferServer.start();
            FileTransferServer.startDownloadServer();

            statusLabel.setText("● Server Running (Port 5555, 5556, 5557)");
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        });
    }

    // === BRIDGE METHODS ===

    public void log(String msg) {
        Platform.runLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            if (logArea != null) {
                logArea.appendText("[" + time + "] " + msg + "\n");
            }
        });
    }

    public void updateClientTable() {
        Platform.runLater(() -> {
            if (clientsPanelRef != null) clientsPanelRef.refreshClients(networkManager.getClients());
        });
    }

    public void updateGroupTable() {
        Platform.runLater(() -> {
            if (groupsPanelRef != null) groupsPanelRef.refreshGroups();
        });
    }

    public ServerNetworkManager getNetworkManager() {
        return networkManager;
    }

    public ControlPanel getControlPanel() {
        return (ControlPanel) mainLayout.getTop();
    }

    public Map<String, ChatPanel> getChatPanels() {
        return new HashMap<>(); // Server UI không cần chat panel thật
    }

    public void openGroupChat(String g) {
    }

    public void closeGroupChatTab(String g) {
    }

    public void closePrivateChatTab(String u) {
    }

    public void appendToGroupChat(String g, String m) {
    }

    public void dissolveGroup(String g) {
        controller.dissolveGroup(g);
    }

    public void stopServer() {
        if (networkManager != null) networkManager.stopServer();
        Platform.exit();
        System.exit(0);
    }

    private void sendBroadcast() {
        String msg = broadcastField.getText().trim();
        if (!msg.isEmpty() && networkManager != null) {
            networkManager.sendServerBroadcast(msg); // Gọi hàm mới bên NetworkManager
            log("[ME - BROADCAST]: " + msg);
            broadcastField.clear();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}