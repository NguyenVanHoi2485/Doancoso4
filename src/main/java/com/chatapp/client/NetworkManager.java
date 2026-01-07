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

    /**
     * Khởi tạo trình quản lý mạng với tham chiếu đến Client chính.
     */
    public NetworkManager(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Thiết lập kết nối Socket tới Server theo IP và Port quy định (5555), đồng thời bắt đầu luồng lắng nghe tin nhắn.
     */
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

    /**
     * Gửi yêu cầu đăng nhập với username và password tới Server.
     */
    public void sendLogin(String username, String password) {
        if (isConnected) {
            this.myUsername = username;
            out.println("LOGIN|" + username + "|" + password);
        }
    }

    /**
     * Gửi yêu cầu đăng ký tài khoản mới tới Server.
     */
    public void sendRegister(String username, String password) {
        if (isConnected) {
            out.println("REGISTER|" + username + "|" + password);
        }
    }

    /**
     * Ngắt kết nối mạng, đóng các luồng dữ liệu (Socket, I/O streams) và cập nhật trạng thái UI.
     */
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

    /**
     * Vòng lặp chạy trên luồng riêng để liên tục đọc tin nhắn từ Server và chuyển giao cho Controller xử lý.
     */
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

    /**
     * Gửi tin nhắn văn bản riêng tư tới một người dùng cụ thể.
     */
    public void sendPrivateMessage(String to, String message) {
        if (isConnected) out.println("PRIVATE|" + to + "|" + message.replace("|", "&#124;"));
    }

    /**
     * Gửi tin nhắn văn bản tới một nhóm chat.
     */
    public void sendGroupMessage(String group, String message) {
        if (isConnected) out.println("GROUP|" + group + "|" + message.replace("|", "&#124;"));
    }

    /**
     * Gửi tin nhắn thông báo toàn hệ thống (Broadcast).
     */
    public void sendBroadcast(String message) {
        if (isConnected) out.println("BROADCAST|" + message.replace("|", "&#124;"));
    }

    /**
     * Gửi thông tin (metadata) về file được gửi riêng tư (không gửi nội dung file ở đây, việc truyền file xử lý qua luồng khác).
     */
    public void sendFileMessage(String to, String fileName, long fileSize, String fileType) {
        if (isConnected)
            out.println("FILE_PRIVATE|" + to + "|" + myUsername + "|" + fileName + "|" + fileSize + "|" + fileType);
    }

    /**
     * Gửi thông tin (metadata) về file được gửi vào nhóm.
     */
    public void sendGroupFileMessage(String group, String fileName, long fileSize, String fileType) {
        if (isConnected)
            out.println("FILE_GROUP|" + group + "|" + myUsername + "|" + fileName + "|" + fileSize + "|" + fileType);
    }

    /**
     * Gửi mã Emoji tới người dùng khác.
     */
    public void sendEmojiMessage(String to, String emojiCode) {
        if (isConnected) out.println("EMOJI_PRIVATE|" + to + "|" + myUsername + "|" + emojiCode);
    }

    /**
     * Gửi mã Emoji vào nhóm chat.
     */
    public void sendGroupEmojiMessage(String group, String emojiCode) {
        if (isConnected) out.println("EMOJI_GROUP|" + group + "|" + myUsername + "|" + emojiCode);
    }

    /**
     * Gửi yêu cầu lấy lại lịch sử chat (Private hoặc Group) từ Server.
     */
    public void requestHistory(String type, String target) {
        if (isConnected) out.println("REQ_HISTORY|" + type + "|" + target);
    }

    /**
     * Gửi yêu cầu lấy danh sách các file đã chia sẻ với một đối tượng cụ thể.
     */
    public void requestFiles(String target) {
        if (isConnected) out.println("REQ_FILES|" + target);
    }

    /**
     * Lấy địa chỉ IP của Server hiện tại (mặc định localhost nếu rỗng).
     */
    public String getServerIp() {
        return (serverIp == null || serverIp.isEmpty()) ? "localhost" : serverIp;
    }

    /**
     * Gửi yêu cầu đặt lại mật khẩu tới Server.
     */
    public void sendResetPassword(String username, String newPassword) {
        if (isConnected) out.println("RESET_PASSWORD|" + username + "|" + newPassword);
    }

    /**
     * Gửi tín hiệu WebRTC (SDP offer/answer hoặc Ice Candidate) để thiết lập cuộc gọi video.
     */
    public void sendWebRTCSignal(String target, String jsonPayload) {
        if (isConnected) {
            String cleanJson = jsonPayload.replace("\n", "").replace("\r", "");
            out.println("WEBRTC|" + target + "|" + cleanJson);
        }
    }

    /**
     * Lấy luồng ghi dữ liệu ra (Writer).
     */
    public PrintWriter getOut() {
        return out;
    }

    /**
     * Lấy tên đăng nhập của người dùng hiện tại.
     */
    public String getMyUsername() {
        return myUsername;
    }

    /**
     * Kiểm tra xem client có đang kết nối tới server hay không.
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Gửi trạng thái "đang soạn tin" (typing) tới đối phương.
     */
    public void sendTyping(String target, boolean isTyping) {
        if (isConnected) {
            String state = isTyping ? "START" : "STOP";
            out.println("TYPING|" + target + "|" + state);
        }
    }

    /**
     * Gửi một gói tin thô bất kỳ tới Server (hàm tiện ích).
     */
    public void sendMessage(String packet) {
        if (isConnected) {
            out.println(packet);
        }
    }
}