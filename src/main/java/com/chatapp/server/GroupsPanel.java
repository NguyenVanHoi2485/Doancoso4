package com.chatapp.server;

import com.chatapp.common.AppLogger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

public class GroupsPanel extends BorderPane {
    private final TableView<GroupRow> groupTable;
    private final ObservableList<GroupRow> groupData;
    private final ChatServer chatServer;

    public static class GroupRow {
        private final String name;
        private final int memberCount;

        public GroupRow(String name, int memberCount) {
            this.name = name;
            this.memberCount = memberCount;
        }

        public String getName() {
            return name;
        }

        public int getMemberCount() {
            return memberCount;
        }
    }

    public GroupsPanel(ChatServer chatServer) {
        this.chatServer = chatServer;
        setPadding(new Insets(10));

        // CSS Class
        getStyleClass().add("panel-box");

        // Tiêu đề
        Label title = new Label("Active Groups");
        title.getStyleClass().add("panel-header");
        title.setMaxWidth(Double.MAX_VALUE);
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 10, 0));

        // TableView
        groupData = FXCollections.observableArrayList();
        groupTable = new TableView<>(groupData);
        groupTable.setPlaceholder(new Label("No groups active"));
        groupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<GroupRow, String> colName = new TableColumn<>("Group Name");
        colName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getName()));

        TableColumn<GroupRow, String> colMembers = new TableColumn<>("Members");
        colMembers.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(
                String.valueOf(data.getValue().getMemberCount())));
        colMembers.setStyle("-fx-alignment: CENTER-RIGHT;"); // Căn phải số lượng

        groupTable.getColumns().addAll(colName, colMembers);

        // Double-click mở chat
        groupTable.setRowFactory(tv -> {
            TableRow<GroupRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    chatServer.openGroupChat(row.getItem().getName());
                }
            });
            return row;
        });

        setCenter(groupTable);

        // Nút điều khiển
        Button dissolveBtn = new Button("Dissolve Selected");
        dissolveBtn.getStyleClass().addAll("button", "danger-button"); // Dùng class CSS đỏ
        dissolveBtn.setOnAction(e -> {
            GroupRow selected = groupTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                chatServer.dissolveGroup(selected.getName());
            } else {
                showAlert("Please select a group to dissolve.");
            }
        });

        HBox buttonBox = new HBox(10, dissolveBtn);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        setBottom(buttonBox);
    }

    public void refreshGroups() {
        groupData.clear();
        chatServer.getNetworkManager().getGroups().forEach((name, group) ->
                groupData.add(new GroupRow(name, group.getMembers().size()))
        );
    }

    public ObservableList<GroupRow> getGroupTableModel() {
        return groupData;
    }

    private void showAlert(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
        a.initOwner(chatServer.getControlPanel().getScene().getWindow());
        a.show();
    }

    public void forceRefresh() {
        refreshGroups();
    }
}