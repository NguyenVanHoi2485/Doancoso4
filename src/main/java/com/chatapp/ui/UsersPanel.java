package com.chatapp.ui;

import com.chatapp.client.ChatClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class UsersPanel extends BorderPane {

    public static class ClientRow {
        private final String username;

        public ClientRow(String username) {
            this.username = username;
        }

        public String getUsername() {
            return username;
        }
    }

    private final ObservableList<ClientRow> clientData;
    private final TableView<ClientRow> clientTable;
    private final ChatClient chatClient;

    /**
     * Khởi tạo giao diện danh sách người dùng Online.
     */
    public UsersPanel(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.clientData = FXCollections.observableArrayList();

        // --- TITLE ---
        Label titleLabel = new Label("Online Users");
        // Style khớp với CSS hiện đại: Font to, màu tối, padding thoáng
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 10 0 5 0; -fx-text-fill: #050505;");

        // --- TABLE CONFIG ---
        clientTable = new TableView<>(clientData);
        clientTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        clientTable.setPlaceholder(new Label("No users online"));

        // Quan trọng: Để Table nhìn giống ListView (tự giãn cột)
        clientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ClientRow, String> col = new TableColumn<>("Username");
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getUsername()));

        // Không cần setPrefWidth cứng nữa vì đã có CONSTRAINED_RESIZE_POLICY
        clientTable.getColumns().add(col);

        clientTable.setOnMouseClicked(this::handleMouseClicked);

        ScrollPane scroll = new ScrollPane(clientTable);
        scroll.setFitToWidth(true);
        // Ẩn border của scrollpane để giao diện phẳng hơn
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // --- BUTTONS ---
        Button privateChatBtn = new Button("Nhắn tin"); // Đổi tiếng Việt cho thân thiện
        privateChatBtn.getStyleClass().add("button"); // Áp dụng CSS button chung
        privateChatBtn.setOnAction(e -> openSelectedPrivateChat());

        // Cho nút full width
        privateChatBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(privateChatBtn, javafx.scene.layout.Priority.ALWAYS);

        HBox buttons = new HBox(8, privateChatBtn);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(5, titleLabel, scroll, buttons);
        content.setPadding(new Insets(10));
        setCenter(content);
    }

    /**
     * Xử lý sự kiện click chuột trên danh sách user (Click đúp để nhắn tin riêng).
     */
    private void handleMouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) openSelectedPrivateChat();
    }

    /**
     * Mở tab chat riêng với người dùng được chọn.
     */
    private void openSelectedPrivateChat() {
        ClientRow selected = clientTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            chatClient.openPrivateChat(selected.getUsername());
        }
    }

    /**
     * Lấy danh sách dữ liệu người dùng Online hiện tại.
     */
    public ObservableList<ClientRow> getClientData() {
        return clientData;
    }
}