package com.chatapp.client;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class NetworkManager {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean isConnected = false;
    private String myUsername;
    private final ChatClient chatClient;
    private String serverIp;

    public NetworkManager(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public boolean connect(String server) {
        this.serverIp = server;
        this.isConnected = false;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(server, 5555), 3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            this.isConnected = true;
            new Thread(this::readMessages).start();
            return true;
        } catch (IOException e) {
            Platform.runLater(() -> {
                new Alert(Alert.AlertType.ERROR, "Không thể kết nối đến server: " + e.getMessage(), ButtonType.OK).show();
            });
            return false;
        }
    }

    public void sendLogin(String username, String password) {
        if (isConnected) {
            this.myUsername = username;
            out.println("LOGIN|" + username + "|" + password);
        }
    }

    public void sendRegister(String username, String password) {
        if (isConnected) {
            out.println("REGISTER|" + username + "|" + password);
        }
    }

    public void disconnect() {
        isConnected = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {
        }
        Platform.runLater(() -> chatClient.appendToBroadcast("Disconnected from server."));
    }

    private void readMessages() {
        try {
            String line;
            while (isConnected && (line = in.readLine()) != null) {
                final String finalLine = line;
                Platform.runLater(() -> {
                    try {
                        if (chatClient.getController() != null) {
                            chatClient.getController().processMessage(finalLine);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            if (isConnected) {
                Platform.runLater(() -> {
                    chatClient.appendToBroadcast("Connection lost: " + e.getMessage());
                    chatClient.disconnect();
                });
            }
        }
    }

    public void sendPrivateMessage(String to, String message) {
        if (isConnected) out.println("PRIVATE|" + to + "|" + message.replace("|", "&#124;"));
    }

    public void sendGroupMessage(String group, String message) {
        if (isConnected) out.println("GROUP|" + group + "|" + message.replace("|", "&#124;"));
    }

    public void sendBroadcast(String message) {
        if (isConnected) out.println("BROADCAST|" + message.replace("|", "&#124;"));
    }

    public void sendFileMessage(String to, String fileName, long fileSize, String fileType) {
        if (isConnected)
            out.println("FILE_PRIVATE|" + to + "|" + myUsername + "|" + fileName + "|" + fileSize + "|" + fileType);
    }

    public void sendGroupFileMessage(String group, String fileName, long fileSize, String fileType) {
        if (isConnected)
            out.println("FILE_GROUP|" + group + "|" + myUsername + "|" + fileName + "|" + fileSize + "|" + fileType);
    }

    public void sendEmojiMessage(String to, String emojiCode) {
        if (isConnected) out.println("EMOJI_PRIVATE|" + to + "|" + myUsername + "|" + emojiCode);
    }

    public void sendGroupEmojiMessage(String group, String emojiCode) {
        if (isConnected) out.println("EMOJI_GROUP|" + group + "|" + myUsername + "|" + emojiCode);
    }

    public void requestHistory(String type, String target) {
        if (isConnected) out.println("REQ_HISTORY|" + type + "|" + target);
    }

    public void requestFiles(String target) {
        if (isConnected) out.println("REQ_FILES|" + target);
    }

    public String getServerIp() {
        return (serverIp == null || serverIp.isEmpty()) ? "localhost" : serverIp;
    }

    public void sendResetPassword(String username, String newPassword) {
        if (isConnected) out.println("RESET_PASSWORD|" + username + "|" + newPassword);
    }

    public void sendWebRTCSignal(String target, String jsonPayload) {
        if (isConnected) {
            String cleanJson = jsonPayload.replace("\n", "").replace("\r", "");
            out.println("WEBRTC|" + target + "|" + cleanJson);
        }
    }

    public PrintWriter getOut() {
        return out;
    }

    public String getMyUsername() {
        return myUsername;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void sendTyping(String target, boolean isTyping) {
        if (isConnected) {
            String state = isTyping ? "START" : "STOP";
            out.println("TYPING|" + target + "|" + state);
        }
    }

    public void sendMessage(String packet) {
        if (isConnected) {
            out.println(packet);
        }
    }
}