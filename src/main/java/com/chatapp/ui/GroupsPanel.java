package com.chatapp.ui;

import com.chatapp.client.ChatClient;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GroupsPanel extends BorderPane {

    public static class GroupRow {
        private final String groupName;

        public GroupRow(String groupName) {
            this.groupName = groupName;
        }

        public String getGroupName() {
            return groupName;
        }
    }

    private final ObservableList<GroupRow> groupData;
    private final TableView<GroupRow> groupTable;
    private final ChatClient chatClient;

    public GroupsPanel(ChatClient chatClient) {
        this.chatClient = chatClient;
        this.groupData = FXCollections.observableArrayList();

        // --- TITLE ---
        Label titleLabel = new Label("My Groups");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 10 0 5 0; -fx-text-fill: #050505;");

        // --- TABLE CONFIG ---
        groupTable = new TableView<>(groupData);
        groupTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        groupTable.setPlaceholder(new Label("No groups joined"));

        // Tự giãn cột
        groupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<GroupRow, String> col = new TableColumn<>("Group Name");
        col.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                data.getValue().getGroupName()));

        groupTable.getColumns().add(col);

        groupTable.setOnMouseClicked(this::handleMouseClicked);

        ScrollPane scroll = new ScrollPane(groupTable);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        // --- BUTTONS ---
        Button createBtn = new Button("+ Tạo");
        Button joinBtn = new Button("Tham gia");
        Button leaveBtn = new Button("Rời");

        // Áp dụng CSS
        createBtn.getStyleClass().add("button");
        joinBtn.getStyleClass().add("button");
        leaveBtn.getStyleClass().addAll("button", "danger-button"); // Nút rời nhóm có màu đỏ (nếu CSS hỗ trợ)

        createBtn.setOnAction(e -> chatClient.createGroup());
        joinBtn.setOnAction(e -> chatClient.joinGroup());
        leaveBtn.setOnAction(e -> leaveSelectedGroup());

        // Layout nút bấm đều nhau
        createBtn.setMaxWidth(Double.MAX_VALUE);
        joinBtn.setMaxWidth(Double.MAX_VALUE);
        leaveBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(createBtn, Priority.ALWAYS);
        HBox.setHgrow(joinBtn, Priority.ALWAYS);
        HBox.setHgrow(leaveBtn, Priority.ALWAYS);

        HBox buttons = new HBox(5, createBtn, joinBtn, leaveBtn);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(5, titleLabel, scroll, buttons);
        content.setPadding(new Insets(10));
        setCenter(content);
    }

    private void handleMouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) openSelectedGroup();
    }

    private void openSelectedGroup() {
        GroupRow row = groupTable.getSelectionModel().getSelectedItem();
        if (row != null) chatClient.openGroupChat(row.getGroupName());
    }

    private void leaveSelectedGroup() {
        GroupRow row = groupTable.getSelectionModel().getSelectedItem();
        if (row != null) chatClient.leaveGroup(row.getGroupName());
    }

    public ObservableList<GroupRow> getGroupData() {
        return groupData;
    }

    public void addGroup(String groupName) {
        Platform.runLater(() -> {
            if (groupData.stream().noneMatch(row -> row.getGroupName().equals(groupName))) {
                groupData.add(new GroupRow(groupName));
            }
        });
    }

    public void removeGroup(String groupName) {
        Platform.runLater(() -> groupData.removeIf(row -> row.getGroupName().equals(groupName)));
    }
}