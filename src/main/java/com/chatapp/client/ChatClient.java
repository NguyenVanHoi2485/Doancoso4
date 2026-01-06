package com.chatapp.client;

import com.chatapp.model.Message;
import com.chatapp.ui.ChatPanel;
import com.chatapp.ui.GroupsPanel;
import com.chatapp.ui.UsersPanel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatClient extends Application {
    private ConnectionPanel connectionPanel;
    private UsersPanel usersPanel;
    private GroupsPanel groupsPanel;
    private TabPane tabbedPane;

    // Quản lý các tab chat đang mở
    private Map<String, ChatPanel> chatPanels = new ConcurrentHashMap<>();
    private Set<String> myGroups = ConcurrentHashMap.newKeySet();

    private NetworkManager networkManager;
    private ChatClientController controller;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        networkManager = new NetworkManager(this);
        controller = new ChatClientController(this, networkManager);

        // Bắt đầu bằng màn hình đăng nhập
        showLoginScene();

        stage.setTitle("Chat Client - Professional Edition (External WebRTC)");
        stage.show();
    }

    // === MÀN HÌNH 1: ĐĂNG NHẬP ===
    public void showLoginScene() {
        connectionPanel = new ConnectionPanel(this);
        StackPane root = new StackPane(connectionPanel);
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #ffffff, #e6f7ff);");
        Scene loginScene = new Scene(root, 400, 520);
        applyCSS(loginScene);
        primaryStage.setScene(loginScene);
        primaryStage.centerOnScreen();
    }

    // === MÀN HÌNH 2: CHAT CHÍNH ===
    public void showMainChatScene() {
        BorderPane mainLayout = new BorderPane();
        HBox leftSide = new HBox();

        // 1. Sidebar Icon
        VBox iconBar = new VBox(15);
        iconBar.setPrefWidth(70);
        iconBar.setStyle("-fx-background-color: #202225; -fx-padding: 15 0 0 0; -fx-alignment: top-center;");

        Button homeBtn = new Button("🏠");
        homeBtn.setTooltip(new Tooltip("Home / Broadcast"));
        homeBtn.setStyle("-fx-background-color: #5865F2; -fx-text-fill: white; -fx-background-radius: 50%; " +
                "-fx-min-width: 45px; -fx-min-height: 45px; -fx-font-size: 20px; -fx-cursor: hand;");
        homeBtn.setOnAction(e -> {
            if (!tabbedPane.getTabs().isEmpty()) tabbedPane.getSelectionModel().select(0);
        });
        iconBar.getChildren().add(homeBtn);

        // 2. Chat List Area
        VBox chatListArea = new VBox(10);
        chatListArea.setPrefWidth(260);
        chatListArea.setStyle("-fx-background-color: #2f3136; -fx-padding: 0 0 10 0;");

        TextField search = new TextField();
        search.setPromptText("Find conversation...");
        search.setStyle("-fx-background-color: #202225; -fx-text-fill: white; -fx-prompt-text-fill: #72767d; -fx-padding: 8; -fx-background-radius: 4;");
        VBox.setMargin(search, new Insets(10));

        usersPanel = new UsersPanel(this);
        groupsPanel = new GroupsPanel(this);
        VBox.setVgrow(usersPanel, Priority.ALWAYS);
        VBox.setVgrow(groupsPanel, Priority.ALWAYS);

        chatListArea.getChildren().addAll(search, usersPanel, groupsPanel);
        leftSide.getChildren().addAll(iconBar, chatListArea);

        // --- CỘT PHẢI ---
        tabbedPane = new TabPane();
        tabbedPane.setStyle("-fx-background-color: #36393f; -fx-background-insets: 0;");

        ChatPanel broadcastPanel = new ChatPanel("📢 All Users", false, networkManager);
        broadcastPanel.hideInputArea();
        chatPanels.put("BROADCAST", broadcastPanel);

        Tab broadcastTab = new Tab("📢 Server Info", broadcastPanel);
        broadcastTab.setClosable(false);
        tabbedPane.getTabs().add(broadcastTab);

        mainLayout.setLeft(leftSide);
        mainLayout.setCenter(tabbedPane);

        Scene chatScene = new Scene(mainLayout, 1100, 750);
        applyCSS(chatScene);
        primaryStage.setScene(chatScene);
        primaryStage.centerOnScreen();
    }

    private void applyCSS(Scene scene) {
        try {
            scene.getStylesheets().add(getClass().getResource("/client-style.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("CSS not found: " + e.getMessage());
        }
    }

    // === LOGIC MỞ TAB CHAT ===
    public void openPrivateChat(String username) {
        if (username.equals(networkManager.getMyUsername())) {
            showAlert("You cannot chat with yourself!");
            return;
        }
        String key = "PRIVATE_" + username;
        ChatPanel panel = chatPanels.get(key);
        if (panel == null) {
            panel = new ChatPanel(username, networkManager.isConnected(), networkManager);
            final String finalUsername = username;
            final ChatPanel finalPanel = panel;

            // 1. Xử lý gửi tin nhắn Text
            panel.setSendAction(msg -> {
                Message message = Message.createTextMessage(msg, networkManager.getMyUsername());
                finalPanel.addMessageAndSort(message);
                networkManager.sendPrivateMessage(finalUsername, msg);
            });

            // 2. [KHÔI PHỤC] Xử lý GỬI File (Đoạn này bị thiếu trong code bạn gửi)
            panel.setSendFileAction((fileName, fileSize, fileType) -> {
                Message fileMessage = Message.createFileMessage(fileName, fileSize, fileType, networkManager.getMyUsername());
                finalPanel.addMessageAndSort(fileMessage);
                networkManager.sendFileMessage(finalUsername, fileName, fileSize, fileType);
            });

            // 3. Xử lý TẢI File (Giữ lại 1 cái thôi)
            panel.setDownloadAction(fileName -> {
                System.out.println("DEBUG: Starting download stream for: " + fileName);
                String serverIp = networkManager.getServerIp();
                FileDownloader.download(serverIp, fileName, this);
            });

            chatPanels.put(key, panel);
            createAndSelectTab("💬 " + finalUsername, panel, key, "PRIVATE", finalUsername);
        } else {
            selectTab(panel);
        }
    }

    public void openGroupChat(String groupName) {
        String key = "GROUP_" + groupName;
        ChatPanel panel = chatPanels.get(key);
        if (panel == null) {
            panel = new ChatPanel(groupName, networkManager.isConnected(), networkManager);
            final String finalGroupName = groupName;
            final ChatPanel finalPanel = panel;

            // Xử lý gửi tin nhắn Text
            panel.setSendAction(msg -> {
                Message message = Message.createTextMessage(msg, networkManager.getMyUsername());
                finalPanel.addMessageAndSort(message);
                networkManager.sendGroupMessage(finalGroupName, msg);
            });

            // Xử lý gửi File
            panel.setSendFileAction((fileName, fileSize, fileType) -> {
                Message fileMessage = Message.createFileMessage(fileName, fileSize, fileType, networkManager.getMyUsername());
                finalPanel.addMessageAndSort(fileMessage);
                networkManager.sendGroupFileMessage(finalGroupName, fileName, fileSize, fileType);
            });

            panel.setDownloadAction(fileName -> {
                System.out.println("DEBUG: Starting group download stream for: " + fileName);
                String serverIp = networkManager.getServerIp();
                FileDownloader.download(serverIp, fileName, this);
            });

            chatPanels.put(key, panel);
            createAndSelectTab("👥 " + finalGroupName, panel, key, "GROUP", finalGroupName);
        } else {
            selectTab(panel);
        }
    }

    private void createAndSelectTab(String title, ChatPanel panel, String key, String type, String target) {
        Platform.runLater(() -> {
            Tab newTab = new Tab(title, panel);
            newTab.setOnCloseRequest(e -> closeChatTab(key));
            newTab.setOnSelectionChanged(e -> {
                if (newTab.isSelected()) {
                    controller.loadHistory(type, target);
                    controller.loadFiles(type + "_" + target);
                }
            });
            tabbedPane.getTabs().add(newTab);
            tabbedPane.getSelectionModel().select(newTab);
        });
    }

    private void selectTab(ChatPanel panel) {
        Platform.runLater(() -> {
            for (Tab tab : tabbedPane.getTabs()) {
                if (tab.getContent() == panel) {
                    tabbedPane.getSelectionModel().select(tab);
                    break;
                }
            }
        });
    }

    // Delegates
    public void createGroup() {
        controller.createGroup();
    }

    public void joinGroup() {
        controller.joinGroup();
    }

    public void leaveGroup(String groupName) {
        controller.leaveGroup(groupName);
    }

    public void closeChatTab(String key) {
        ChatPanel panel = chatPanels.get(key);
        if (panel == null) return;
        Platform.runLater(() -> {
            tabbedPane.getTabs().removeIf(tab -> tab.getContent() == panel);
            chatPanels.remove(key);
        });
    }

    public void connect(String server) {
        networkManager.connect(server);
    }

    public void disconnect() {
        networkManager.disconnect();
        if (connectionPanel != null) connectionPanel.setEnabled(true);
    }

    // Getters
    public Map<String, ChatPanel> getChatPanels() {
        return chatPanels;
    }

    public Set<String> getMyGroups() {
        return myGroups;
    }

    public UsersPanel getUsersPanel() {
        return usersPanel;
    }

    public GroupsPanel getGroupsPanel() {
        return groupsPanel;
    }

    public TabPane getTabbedPane() {
        return tabbedPane;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public ChatClientController getController() {
        return controller;
    }

    public ConnectionPanel getConnectionPanel() {
        return connectionPanel;
    }

    public void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.initOwner(getPrimaryStage());
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void appendToBroadcast(String messageText) {
        ChatPanel broadcastPanel = chatPanels.get("BROADCAST");
        if (broadcastPanel != null) {
            Message systemMsg = Message.createSystemMessage(messageText);
            Platform.runLater(() -> broadcastPanel.addMessageAndSort(systemMsg));
        }
    }

    public void selectBroadcastTab() {
        Platform.runLater(() -> {
            if (!tabbedPane.getTabs().isEmpty()) {
                tabbedPane.getSelectionModel().select(0);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}