package com.chatapp.client;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class ConnectionPanel extends VBox {

    private TextField serverField;
    private TextField usernameField;
    private PasswordField passwordField;
    private Button connectButton;
    private final ChatClient chatClient;

    public ConnectionPanel(ChatClient chatClient) {
        this.chatClient = chatClient;

        setAlignment(Pos.CENTER);
        setSpacing(20);
        setPadding(new Insets(30));

        setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-padding: 30;");
        setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.1)));
        setMaxWidth(320);
        setMaxHeight(450); // Tăng chiều cao một chút

        Label title = new Label("CHAT LOGIN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        title.setStyle("-fx-text-fill: #0084ff;");

        VBox serverBox = createInputBox("Server IP", "localhost");
        serverField = (TextField) serverBox.getChildren().get(1);

        VBox userBox = createInputBox("Username", "Nhập tên tài khoản");
        usernameField = (TextField) userBox.getChildren().get(1);

        Label passLabel = new Label("Password");
        passLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Nhập mật khẩu");
        passwordField.setPrefHeight(35);
        passwordField.setStyle("-fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");
        VBox passBox = new VBox(5, passLabel, passwordField);

        connectButton = new Button("ĐĂNG NHẬP");
        connectButton.setPrefWidth(280);
        connectButton.setPrefHeight(40);
        connectButton.setStyle("-fx-background-color: #0084ff; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-cursor: hand;");
        connectButton.setOnAction(e -> handleLogin());

        // --- THÊM LINK QUÊN MẬT KHẨU ---
        Hyperlink forgotPassLink = new Hyperlink("Quên mật khẩu?");
        forgotPassLink.setStyle("-fx-text-fill: #dc3545; -fx-font-size: 12px;");
        forgotPassLink.setOnAction(e -> showForgotPasswordDialog());

        Hyperlink registerLink = new Hyperlink("Chưa có tài khoản? Đăng ký ngay");
        registerLink.setStyle("-fx-text-fill: #666; -fx-underline: false;");
        registerLink.setOnAction(e -> showRegisterDialog());

        getChildren().addAll(title, serverBox, userBox, passBox, connectButton, forgotPassLink, registerLink);
    }

    private VBox createInputBox(String labelText, String prompt) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setPrefHeight(35);
        field.setStyle("-fx-background-radius: 5; -fx-border-color: #ddd; -fx-border-radius: 5;");
        return new VBox(5, label, field);
    }

    private void handleLogin() {
        String server = serverField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();

        if (server.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showAlert("Vui lòng nhập đầy đủ thông tin!", Alert.AlertType.WARNING);
            return;
        }
        if (chatClient.getNetworkManager().connect(server)) {
            setEnabled(false);
            chatClient.getNetworkManager().sendLogin(user, pass);
        }
    }

    // --- DIALOG QUÊN MẬT KHẨU ---
    private void showForgotPasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Quên mật khẩu");
        dialog.setHeaderText("Đặt lại mật khẩu mới");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField userF = new TextField();
        userF.setPromptText("Tên đăng nhập cũ");
        PasswordField passF = new PasswordField();
        passF.setPromptText("Mật khẩu mới");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(userF, 1, 0);
        grid.add(new Label("New Pass:"), 0, 1);
        grid.add(passF, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                String u = userF.getText().trim();
                String p = passF.getText();
                if (!u.isEmpty() && !p.isEmpty()) {
                    String server = serverField.getText().trim();
                    // Kết nối tạm thời để gửi lệnh Reset
                    if (chatClient.getNetworkManager().connect(server)) {
                        chatClient.getNetworkManager().sendResetPassword(u, p);
                    }
                } else {
                    showAlert("Vui lòng nhập đủ thông tin!", Alert.AlertType.WARNING);
                }
            }
        });
    }

    private void showRegisterDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("Đăng ký tài khoản");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField userF = new TextField();
        PasswordField passF = new PasswordField();

        grid.add(new Label("Tên đăng nhập:"), 0, 0);
        grid.add(userF, 1, 0);
        grid.add(new Label("Mật khẩu:"), 0, 1);
        grid.add(passF, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(type -> {
            if (type == ButtonType.OK) {
                String u = userF.getText().trim();
                String p = passF.getText();
                if (!u.isEmpty() && !p.isEmpty()) {
                    String server = serverField.getText().trim();
                    if (chatClient.getNetworkManager().connect(server)) {
                        chatClient.getNetworkManager().sendRegister(u, p);
                    }
                }
            }
        });
    }

    public void setEnabled(boolean enabled) {
        connectButton.setDisable(!enabled);
        serverField.setDisable(!enabled);
        usernameField.setDisable(!enabled);
        passwordField.setDisable(!enabled);
    }

    public void showSuccess(String username) {
    }

    private void showAlert(String msg, Alert.AlertType type) {
        new Alert(type, msg, ButtonType.OK).show();
    }

    public String getServer() {
        return serverField.getText();
    }

    public String getUsername() {
        return usernameField.getText();
    }
}