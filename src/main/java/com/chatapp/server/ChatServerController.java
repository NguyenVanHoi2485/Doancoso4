package com.chatapp.server;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Optional;

public class ChatServerController {
    private final ChatServer chatServer;
    private final ServerNetworkManager networkManager;

    public ChatServerController(ChatServer chatServer, ServerNetworkManager networkManager) {
        this.chatServer = chatServer;
        this.networkManager = networkManager;
    }

    public void dissolveGroup(String groupName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Dissolve Group");
        confirm.setHeaderText("Dissolve group: " + groupName);
        confirm.setContentText("This action cannot be undone. All group data will be deleted from Database.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            networkManager.handleDissolveGroup(groupName);
            showInfo("Group '" + groupName + "' has been dissolved and deleted from database.");
        }
    }

    // === HỘP THOẠI THÔNG BÁO ===
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(getWindow());
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(getWindow());
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Stage getWindow() {
        return (Stage) chatServer.getControlPanel().getScene().getWindow();
    }
}