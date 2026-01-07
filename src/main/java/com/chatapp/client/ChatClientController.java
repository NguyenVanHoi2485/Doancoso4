package com.chatapp.client;

import com.chatapp.common.AppLogger;
import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import com.chatapp.ui.ChatPanel;
import com.chatapp.ui.GroupsPanel;
import com.chatapp.ui.UsersPanel;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.*;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javafx.scene.control.Tab;

public class ChatClientController {
    private final ChatClient chatClient;
    private final NetworkManager networkManager;

    /**
     * Khởi tạo controller với tham chiếu đến Client UI và Network Manager.
     */
    public ChatClientController(ChatClient chatClient, NetworkManager networkManager) {
        this.chatClient = chatClient;
        this.networkManager = networkManager;
    }

    /**
     * Xử lý các tin nhắn thô nhận được từ server, phân loại theo command và điều hướng đến các hàm xử lý cụ thể.
     */
    public void processMessage(String message) {
        try {
            // Tách tin nhắn, giới hạn split là -1 để giữ cả chuỗi rỗng nếu có
            String[] parts = message.split("\\|", -1);
            if (parts.length == 0) return;
            String command = parts[0];

            switch (command) {
                case "BROADCAST":
                    if (parts.length > 1) {
                        // 1. Hiển thị tin nhắn (Code cũ)
                        chatClient.appendToBroadcast(parts[1].replace("&#124;", "|"));

                        // 2. [THÊM MỚI] Tự động chuyển tab
                        chatClient.selectBroadcastTab();
                    }
                    break;

                case "PRIVATE":
                    if (parts.length >= 3)
                        receivePrivateMessage(parts[1], parts[2]);
                    break;

                case "GROUP":
                    if (parts.length >= 3)
                        receiveGroupMessage(parts[1], parts[2]);
                    break;

                // === TIN NHẮN FILE (CHỈ LÀ THÔNG BÁO CÓ FILE) ===
                case "FILE_PRIVATE":
                    if (parts.length >= 6) {
                        String sender = parts[1];
                        String receiver = parts[2];
                        String fileName = parts[3];
                        long fileSize = Long.parseLong(parts[4]);
                        String fileType = parts[5];

                        if (receiver.equals(networkManager.getMyUsername())) {
                            receivePrivateFileMessage(sender, fileName, fileSize, fileType);
                        }
                    }
                    break;

                case "FILE_GROUP":
                    if (parts.length >= 6) {
                        String sender = parts[1];
                        String groupName = parts[2];
                        String fileName = parts[3];
                        long fileSize = Long.parseLong(parts[4]);
                        String fileType = parts[5];
                        receiveGroupFileMessage(sender, groupName, fileName, fileSize, fileType);
                    }
                    break;
                case "FILE_CONTENT":
                    if (parts.length >= 3) {
                        String fileName = parts[1];
                        String base64Data = parts[2];

                        Platform.runLater(() -> {
                            try {
                                // 1. Giải mã Base64
                                byte[] fileBytes = Base64.getDecoder().decode(base64Data);

                                // 2. Tạo thư mục 'downloads' nếu chưa có
                                File downloadDir = new File("client_downloads");
                                if (!downloadDir.exists()) downloadDir.mkdir();

                                // 3. Lưu file
                                File destFile = new File(downloadDir, fileName);
                                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                                    fos.write(fileBytes);
                                }

                                // 4. Thông báo
                                chatClient.showAlert("Đã tải xong file!\nLưu tại: " + destFile.getAbsolutePath());
                                AppLogger.info("File saved to: " + destFile.getAbsolutePath());

                            } catch (Exception e) {
                                chatClient.showAlert("Lỗi khi lưu file: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    }
                    break;
                // === TIN NHẮN EMOJI ===
                case "EMOJI_PRIVATE":
                    if (parts.length >= 4)
                        receivePrivateEmojiMessage(parts[1], parts[3]);
                    break;

                case "EMOJI_GROUP":
                    if (parts.length >= 4)
                        receiveGroupEmojiMessage(parts[1], parts[2], parts[3]);
                    break;

                // === QUẢN LÝ NHÓM & USER ===
                case "GROUP_LIST":
                    if (parts.length > 1 && !parts[1].isEmpty())
                        handleGroupList(parts[1]);
                    break;

                case "GROUP_CREATED":
                case "GROUP_JOINED":
                    if (parts.length >= 2)
                        handleGroupJoined(parts[1]);
                    break;

                case "GROUP_LEFT":
                case "GROUP_DISSOLVED":
                    if (parts.length >= 2)
                        handleGroupLeft(parts[1], command);
                    break;

                case "USER_LIST":
                    if (parts.length > 1)
                        Platform.runLater(() -> updateUserList(parts[1].split(",")));
                    break;

                case "USER_JOINED":
                    if (parts.length > 1 && !parts[1].equals(networkManager.getMyUsername()))
                        Platform.runLater(() -> addUser(parts[1]));
                    break;

                case "USER_LEFT":
                    if (parts.length > 1)
                        Platform.runLater(() -> removeUser(parts[1]));
                    break;

                // === XÁC THỰC ===
                case "LOGIN_SUCCESS":
                    if (parts.length > 1) handleLoginSuccess(parts[1]);
                    break;
                case "LOGIN_FAIL":
                    if (parts.length > 1) handleLoginFail(parts[1]);
                    break;
                case "REGISTER_SUCCESS":
                    if (parts.length > 1) handleRegisterSuccess(parts[1]);
                    break;
                case "REGISTER_FAIL":
                    if (parts.length > 1) handleRegisterFail(parts[1]);
                    break;
                case "ERROR":
                    if (parts.length > 1) showError(parts[1]);
                    break;

                // === [MỚI] XỬ LÝ PHẢN HỒI DOWNLOAD ===
                case "DOWNLOAD_ERROR":
                    if (parts.length > 1) {
                        String errorMsg = parts[1];
                        Platform.runLater(() -> chatClient.showAlert("Lỗi tải file: " + errorMsg));
                    }
                    break;

                // === DỮ LIỆU LỊCH SỬ ===
                case "HISTORY_DATA":
                    if (parts.length >= 6) {
                        receiveHistoryData(parts[1], parts[2], parts[3], parts[4].replace("&#124;", "|"), parts[5]);
                    }
                    break;

                case "FILES_DATA":
                    if (parts.length >= 6) {
                        receiveFilesData(parts[1], parts[2], Long.parseLong(parts[3]), parts[4], parts[5]);
                    }
                    break;

                // === VIDEO CALL & WEBRTC ===
                case "WEBRTC":
                    if (parts.length >= 3)
                        Platform.runLater(() -> handleIncomingWebRTC(parts[1], message.substring(message.indexOf(parts[2]))));
                    break;

                case "CALL_REQ":
                    if (parts.length >= 4)
                        Platform.runLater(() -> showIncomingCallDialog(parts[1], Long.parseLong(parts[2]), parts[3]));
                    break;

                case "CALL_LOG":
                    if (parts.length >= 5) handleCallLog(parts[1], parts[2], parts[3], parts[4]);
                    break;

                case "CALL_RES":
                    if (parts.length >= 4) handleCallResponse(parts[1], parts[2], Long.parseLong(parts[3]));
                    break;

                case "CALL_END":
                    if (parts.length >= 2) handleCallEnd(parts[1]);
                    break;

                case "TYPING":
                    if (parts.length >= 4) {
                        String sender = parts[1];
                        String target = parts[2];
                        String state = parts[3];

                        boolean isTyping = "START".equals(state);

                        String tabKey;
                        if (target.equals(networkManager.getMyUsername())) {
                            tabKey = "PRIVATE_" + sender;
                        } else {
                            tabKey = "GROUP_" + target;
                        }

                        Platform.runLater(() -> {
                            ChatPanel panel = chatClient.getChatPanels().get(tabKey);
                            if (panel != null) {
                                panel.showTyping(sender, isTyping);
                            }
                        });
                    }
                    break;
                case "VOICE_PRIVATE":
                case "VOICE_GROUP":
                    if (parts.length >= 6) {
                        String sender = parts[1];
                        String target = parts[2];
                        String fileName = parts[3];

                        // Xác định Tab
                        String tabName;
                        if (command.equals("VOICE_PRIVATE")) {
                            tabName = "PRIVATE_" + sender;
                        } else {
                            tabName = "GROUP_" + target;
                        }

                        Platform.runLater(() -> {
                            ChatPanel panel = chatClient.getChatPanels().get(tabName);
                            if (panel != null) {
                                // --- SỬA LỖI TẠI ĐÂY ---

                                Message msg = new Message(MessageType.VOICE, fileName, sender, LocalDateTime.now());

                                msg.setFileName(fileName);

                                panel.appendMessage(msg);
                            }
                        });
                    }
                    break;
            }
        } catch (Exception e) {
            AppLogger.severe("Error processing message: " + message, e);
        }
    }

    // ——————————————————————— LOGIC XỬ LÝ CHI TIẾT ———————————————————————

    /**
     * Yêu cầu server gửi lại lịch sử tin nhắn của một cuộc hội thoại (cá nhân hoặc nhóm) khi người dùng mở tab.
     */
    public void loadHistory(String type, String target) {
        String key = type.equals("PRIVATE") ? "PRIVATE_" + target : "GROUP_" + target;
        ChatPanel panel = chatClient.getChatPanels().get(key);
        if (panel != null) {
            panel.clearMessages();
            networkManager.requestHistory(type, target);
        }
    }

    /**
     * Yêu cầu server gửi danh sách các file đã chia sẻ trong cuộc hội thoại.
     */
    public void loadFiles(String target) {
        ChatPanel panel = chatClient.getChatPanels().get(target);
        if (panel != null) {
            networkManager.requestFiles(target);
        }
    }

    // === NHẬN DỮ LIỆU LỊCH SỬ ===

    /**
     * Nhận dữ liệu lịch sử tin nhắn dạng text từ server và hiển thị lên giao diện chat.
     */
    private void receiveHistoryData(String type, String target, String sender, String content, String timeStr) {
        Platform.runLater(() -> {
            String key = type.equals("PRIVATE") ? "PRIVATE_" + target : "GROUP_" + target;
            ChatPanel panel = chatClient.getChatPanels().get(key);

            if (panel != null) {
                LocalDateTime timestamp = parseTime(timeStr);
                Message msg;
                if (isCallContent(content)) {
                    msg = new Message(com.chatapp.model.MessageType.CALL, content, sender, timestamp);
                } else {
                    msg = Message.createTextMessage(content, sender).withTimestamp(timestamp);
                }
                panel.addMessageAndSort(msg);
            }
        });
    }

    /**
     * Nhận dữ liệu lịch sử tin nhắn dạng file từ server và hiển thị lên giao diện chat.
     */
    private void receiveFilesData(String targetKey, String fileName, long size, String sender, String timeStr) {
        Platform.runLater(() -> {
            ChatPanel panel = chatClient.getChatPanels().get(targetKey);
            if (panel != null) {
                LocalDateTime timestamp = parseTime(timeStr);
                Message fileMsg = Message.createFileMessage(fileName, size, "unknown", sender)
                        .withTimestamp(timestamp);
                panel.addMessageAndSort(fileMsg);
            }
        });
    }

    // === NHẬN TIN NHẮN REALTIME ===

    /**
     * Xử lý tin nhắn văn bản riêng tư nhận được: mở tab chat (nếu chưa mở) và hiển thị nội dung.
     */
    private void receivePrivateMessage(String from, String msg) {
        String decodedMsg = msg.replace("&#124;", "|");
        Message message = Message.createTextMessage(decodedMsg, from);

        Platform.runLater(() -> {
            chatClient.openPrivateChat(from);
            ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + from);
            if (panel != null) panel.appendMessage(message);
        });
    }

    /**
     * Xử lý tin nhắn nhóm nhận được: mở tab chat nhóm và hiển thị nội dung (loại trừ tin nhắn do chính mình gửi).
     */
    private void receiveGroupMessage(String groupName, String msg) {
        String decodedMsg = msg.replace("&#124;", "|");
        String sender = "Unknown";
        String content = decodedMsg.trim();

        int colonIndex = content.indexOf(": ");
        if (colonIndex > 0) {
            sender = content.substring(0, colonIndex).trim();
            content = content.substring(colonIndex + 2).trim();
        }

        if (sender.equals(networkManager.getMyUsername())) return;

        Message message = Message.createTextMessage(decodedMsg, sender);
        Platform.runLater(() -> {
            chatClient.openGroupChat(groupName);
            ChatPanel panel = chatClient.getChatPanels().get("GROUP_" + groupName);
            if (panel != null) panel.appendMessage(message);
        });
    }

    /**
     * Xử lý thông báo có file gửi riêng: hiển thị bong bóng chat chứa thông tin file để người dùng tải về.
     */
    private void receivePrivateFileMessage(String from, String fileName, long fileSize, String fileType) {
        if (from.equals(networkManager.getMyUsername())) return;
        Message message = Message.createFileMessage(fileName, fileSize, fileType, from);
        Platform.runLater(() -> {
            chatClient.openPrivateChat(from);
            ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + from);
            if (panel != null) panel.appendMessage(message);
        });
    }

    /**
     * Xử lý thông báo có file gửi vào nhóm: hiển thị bong bóng chat chứa thông tin file.
     */
    private void receiveGroupFileMessage(String from, String groupName, String fileName, long fileSize, String fileType) {
        if (from.equals(networkManager.getMyUsername())) return;
        Message message = Message.createFileMessage(fileName, fileSize, fileType, from);
        Platform.runLater(() -> {
            chatClient.openGroupChat(groupName);
            ChatPanel panel = chatClient.getChatPanels().get("GROUP_" + groupName);
            if (panel != null) panel.appendMessage(message);
        });
    }

    /**
     * Xử lý tin nhắn Emoji riêng tư.
     */
    private void receivePrivateEmojiMessage(String from, String emojiCode) {
        Message message = Message.createEmojiMessage(emojiCode, from);
        Platform.runLater(() -> {
            chatClient.openPrivateChat(from);
            ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + from);
            if (panel != null) panel.appendMessage(message);
        });
    }

    /**
     * Xử lý tin nhắn Emoji trong nhóm.
     */
    private void receiveGroupEmojiMessage(String from, String groupName, String emojiCode) {
        Message message = Message.createEmojiMessage(emojiCode, from);
        Platform.runLater(() -> {
            chatClient.openGroupChat(groupName);
            ChatPanel panel = chatClient.getChatPanels().get("GROUP_" + groupName);
            if (panel != null) panel.appendMessage(message);
        });
    }

    // === XỬ LÝ NHÓM & USER ===

    /**
     * Xử lý danh sách các nhóm mà người dùng đang tham gia (nhận được khi vừa đăng nhập).
     */
    private void handleGroupList(String rawList) {
        String[] groupNames = rawList.split(",");
        Platform.runLater(() -> {
            for (String groupName : groupNames) {
                if (!groupName.trim().isEmpty()) {
                    chatClient.getMyGroups().add(groupName);
                    chatClient.getGroupsPanel().addGroup(groupName);
                }
            }
        });
    }

    /**
     * Xử lý sự kiện khi người dùng tham gia hoặc tạo thành công một nhóm mới.
     */
    private void handleGroupJoined(String group) {
        chatClient.getMyGroups().add(group);
        Platform.runLater(() -> {
            chatClient.getGroupsPanel().addGroup(group);
            updateGroupList();
            appendToBroadcast("You joined group: " + group);
        });
    }

    /**
     * Xử lý sự kiện khi người dùng rời nhóm hoặc nhóm bị giải tán.
     */
    private void handleGroupLeft(String group, String command) {
        chatClient.getMyGroups().remove(group);
        Platform.runLater(() -> {
            chatClient.getGroupsPanel().removeGroup(group);
            updateGroupList();
            closeGroupTab(group);
            appendToBroadcast(command.equals("GROUP_LEFT") ? "You left group: " + group : "Group dissolved: " + group);
        });
    }

    // === XỬ LÝ CALL LOGIC ===

    /**
     * Xử lý log cuộc gọi (lịch sử cuộc gọi nhỡ, kết thúc...) để hiển thị vào khung chat.
     */
    private void handleCallLog(String sender, String receiver, String content, String timeStr) {
        String myName = networkManager.getMyUsername();
        String target = sender.equals(myName) ? receiver : sender;
        Platform.runLater(() -> {
            ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + target);
            if (panel != null) {
                LocalDateTime timestamp = parseTime(timeStr);
                Message msg = new Message(com.chatapp.model.MessageType.CALL, content, sender, timestamp);
                panel.addMessageAndSort(msg);
                panel.setCallEnded();
            }
        });
    }

    /**
     * Xử lý phản hồi cuộc gọi: nếu đối phương chấp nhận, bắt đầu thiết lập Video Call.
     */
    private void handleCallResponse(String responder, String status, long callId) {
        if ("ACCEPTED".equals(status)) {
            Platform.runLater(() -> {
                ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + responder);
                if (panel != null) {
                    panel.setCallStarted(callId);
                    panel.startExternalVideoCall(true);
                }
            });
        }
    }

    /**
     * Xử lý sự kiện kết thúc cuộc gọi từ phía đối phương.
     */
    private void handleCallEnd(String otherParty) {
        Platform.runLater(() -> {
            ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + otherParty);
            if (panel != null) panel.setCallEnded();
        });
    }

    // === UTILITIES ===

    /**
     * Kiểm tra xem nội dung tin nhắn có phải là thông tin hệ thống về cuộc gọi hay không.
     */
    private boolean isCallContent(String content) {
        return content.startsWith("ENDED|") || content.startsWith("MISSED|") || content.startsWith("REJECTED|");
    }

    /**
     * Chuyển đổi chuỗi thời gian từ server thành đối tượng LocalDateTime.
     */
    private LocalDateTime parseTime(String timeStr) {
        try {
            String cleanTime = timeStr.contains(".") ? timeStr.substring(0, timeStr.lastIndexOf(".")) : timeStr;
            return LocalDateTime.parse(cleanTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    // === LOGIN / REGISTER / UPDATE UI ===

    /**
     * Xử lý khi đăng nhập thành công: chuyển sang màn hình chat chính.
     */
    private void handleLoginSuccess(String username) {
        Platform.runLater(() -> chatClient.showMainChatScene());
    }

    /**
     * Xử lý khi đăng nhập thất bại: hiện thông báo lỗi và cho phép thử lại.
     */
    private void handleLoginFail(String reason) {
        Platform.runLater(() -> {
            chatClient.showAlert("Đăng nhập thất bại: " + reason);
            chatClient.disconnect();
            chatClient.getConnectionPanel().setEnabled(true);
        });
    }

    /**
     * Xử lý khi đăng ký tài khoản thành công.
     */
    private void handleRegisterSuccess(String msg) {
        Platform.runLater(() -> {
            chatClient.showAlert(msg);
            chatClient.disconnect();
            chatClient.getConnectionPanel().setEnabled(true);
        });
    }

    /**
     * Xử lý khi đăng ký tài khoản thất bại.
     */
    private void handleRegisterFail(String msg) {
        Platform.runLater(() -> {
            chatClient.showAlert("Đăng ký lỗi: " + msg);
            chatClient.disconnect();
            chatClient.getConnectionPanel().setEnabled(true);
        });
    }

    /**
     * Thêm tin nhắn vào tab thông báo chung (Broadcast).
     */
    private void appendToBroadcast(String text) {
        Message message = Message.createSystemMessage(text);
        Platform.runLater(() -> {
            ChatPanel broadcast = chatClient.getChatPanels().get("BROADCAST");
            if (broadcast != null) broadcast.appendMessage(message);
        });
    }

    /**
     * Cập nhật toàn bộ danh sách người dùng online trên giao diện.
     */
    private void updateUserList(String[] users) {
        ObservableList<UsersPanel.ClientRow> data = chatClient.getUsersPanel().getClientData();
        data.clear();
        for (String u : users) {
            if (!u.isEmpty() && !u.equals(networkManager.getMyUsername())) {
                data.add(new UsersPanel.ClientRow(u));
            }
        }
    }

    /**
     * Thêm một người dùng mới vào danh sách online (khi họ vừa đăng nhập).
     */
    private void addUser(String username) {
        ObservableList<UsersPanel.ClientRow> data = chatClient.getUsersPanel().getClientData();
        if (data.stream().noneMatch(row -> row.getUsername().equals(username))) {
            data.add(new UsersPanel.ClientRow(username));
        }
    }

    /**
     * Xóa một người dùng khỏi danh sách online (khi họ đăng xuất).
     */
    private void removeUser(String username) {
        chatClient.getUsersPanel().getClientData().removeIf(row -> row.getUsername().equals(username));
    }

    /**
     * Cập nhật hiển thị danh sách nhóm trên giao diện người dùng.
     */
    public void updateGroupList() {
        ObservableList<GroupsPanel.GroupRow> data = chatClient.getGroupsPanel().getGroupData();
        data.setAll(chatClient.getMyGroups().stream().map(GroupsPanel.GroupRow::new).toList());
    }

    /**
     * Đóng tab chat của một nhóm cụ thể (thường dùng khi rời nhóm).
     */
    private void closeGroupTab(String groupName) {
        String key = "GROUP_" + groupName;
        ChatPanel panel = chatClient.getChatPanels().get(key);
        if (panel != null) {
            Platform.runLater(() -> {
                chatClient.getTabbedPane().getTabs().removeIf(tab -> tab.getContent() == panel);
                chatClient.getChatPanels().remove(key);
            });
        }
    }

    /**
     * Hiển thị hộp thoại báo lỗi chung.
     */
    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(chatClient.getPrimaryStage());
        a.showAndWait();
    }

    // === WEBRTC / CALL DIALOGS ===

    /**
     * Điều phối tín hiệu WebRTC nhận được tới đúng panel chat để xử lý kết nối video/audio.
     */
    private void handleIncomingWebRTC(String sender, String jsonPayload) {
        Platform.runLater(() -> {
            String privateKey = "PRIVATE_" + sender;
            ChatPanel panel = chatClient.getChatPanels().get(privateKey);
            if (panel == null) {
                chatClient.openPrivateChat(sender);
                panel = chatClient.getChatPanels().get(privateKey);
            }
            if (panel != null) {
                for (Tab tab : chatClient.getTabbedPane().getTabs()) {
                    if (tab.getText().contains(sender)) {
                        chatClient.getTabbedPane().getSelectionModel().select(tab);
                        break;
                    }
                }
                panel.handleIncomingSignal(jsonPayload);
            }
        });
    }

    /**
     * Hiển thị hộp thoại thông báo có cuộc gọi đến và cho phép người dùng chấp nhận hoặc từ chối.
     */
    private void showIncomingCallDialog(String sender, long callId, String type) {
        chatClient.openPrivateChat(sender);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cuộc gọi đến");
        alert.setHeaderText(sender + " đang gọi video...");
        alert.setContentText("Bạn có muốn trả lời không?");
        ButtonType btnAccept = new ButtonType("Trả lời");
        ButtonType btnReject = new ButtonType("Từ chối", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnAccept, btnReject);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == btnAccept) {
            networkManager.getOut().println("CALL_RES|" + sender + "|ACCEPTED|" + callId);
            Platform.runLater(() -> {
                ChatPanel panel = chatClient.getChatPanels().get("PRIVATE_" + sender);
                if (panel != null) {
                    panel.setCallStarted(callId);
                    panel.startExternalVideoCall(false);
                }
            });
        } else {
            networkManager.getOut().println("CALL_RES|" + sender + "|REJECTED|" + callId);
        }
    }

// === DELEGATE METHODS (XỬ LÝ GROUP) ===

    /**
     * Hiển thị hộp thoại nhập tên nhóm và gửi yêu cầu tạo nhóm lên server.
     */
    public void createGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tạo nhóm mới");
        dialog.setHeaderText("Nhập tên nhóm muốn tạo:");
        dialog.setContentText("Tên nhóm:");

        dialog.showAndWait().ifPresent(groupName -> {
            if (!groupName.trim().isEmpty()) {
                // Gửi lệnh: CREATE_GROUP|TenNhom
                // (Có thể mở rộng thêm danh sách thành viên nếu muốn, hiện tại tạo nhóm rỗng trước)
                networkManager.getOut().println("CREATE_GROUP|" + groupName);
            }
        });
    }

    /**
     * Hiển thị hộp thoại nhập tên nhóm và gửi yêu cầu tham gia nhóm lên server.
     */
    public void joinGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tham gia nhóm");
        dialog.setHeaderText("Nhập tên nhóm muốn tham gia:");
        dialog.setContentText("Tên nhóm:");

        dialog.showAndWait().ifPresent(groupName -> {
            if (!groupName.trim().isEmpty()) {
                networkManager.getOut().println("JOIN_GROUP|" + groupName);
            }
        });
    }

    /**
     * Hiển thị xác nhận và gửi yêu cầu rời khỏi nhóm lên server.
     */
    public void leaveGroup(String groupName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Rời nhóm");
        alert.setHeaderText("Bạn có chắc muốn rời nhóm: " + groupName + "?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                networkManager.getOut().println("LEAVE_GROUP|" + groupName);
            }
        });
    }
}