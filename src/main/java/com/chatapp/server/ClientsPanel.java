package com.chatapp.server;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;

public class ClientsPanel extends BorderPane {
    private final TableView<ClientRow> clientTable;
    private final ObservableList<ClientRow> clientData;
    private final ChatServer chatServer;

    public static class ClientRow {
        private final String username;
        private final String ipAddress;

        public ClientRow(String username, String ipAddress) {
            this.username = username;
            this.ipAddress = ipAddress;
        }

        public String getUsername() {
            return username;
        }

        public String getIpAddress() {
            return ipAddress;
        }
    }

    public ClientsPanel(ChatServer chatServer) {
        this.chatServer = chatServer;
        setPadding(new Insets(10));

        // CSS Class cho toàn bộ Panel
        getStyleClass().add("panel-box");

        // Tiêu đề
        Label title = new Label("Online Clients");
        title.getStyleClass().add("panel-header"); // Class CSS header
        title.setMaxWidth(Double.MAX_VALUE);
        setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 10, 0));

        // TableView
        clientData = FXCollections.observableArrayList();
        clientTable = new TableView<>(clientData);
        clientTable.setPlaceholder(new Label("No clients connected"));
        clientTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // Tự giãn cột

        TableColumn<ClientRow, String> colUser = new TableColumn<>("Username");
        colUser.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));

        TableColumn<ClientRow, String> colIP = new TableColumn<>("IP Address");
        colIP.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getIpAddress()));

        clientTable.getColumns().addAll(colUser, colIP);
        setCenter(clientTable);
    }

    public void refreshClients(java.util.Map<String, ClientHandler> clients) {
        clientData.clear();
        clients.forEach((username, handler) -> {
            String ip = handler.getSocket().getInetAddress().getHostAddress();
            clientData.add(new ClientRow(username, ip));
        });
    }

    public ObservableList<ClientRow> getClientTableModel() {
        return clientData;
    }
}