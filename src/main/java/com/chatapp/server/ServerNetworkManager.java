package com.chatapp.server;

import com.chatapp.common.AppLogger;
import com.chatapp.common.DBHelper;
import com.chatapp.model.Message;
import com.chatapp.model.ChatGroup;
import javafx.application.Platform;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.CompletableFuture;

public class ServerNetworkManager {
    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, ChatGroup> groups = new ConcurrentHashMap<>();
    private boolean isRunning = false;
    private static final int PORT = 5555;
    private final ChatServer chatServer;
    private final Set<ClientHandler> allHandlers = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> callStartTimes = new ConcurrentHashMap<>();

    private final Map<String, Long> userActiveCalls = new ConcurrentHashMap<>();

    public ServerNetworkManager(ChatServer chatServer) {
        this.chatServer = chatServer;
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            initSystemUser();
            isRunning = true;
            Platform.runLater(() -> {
                chatServer.getControlPanel().setRunning(true);
                chatServer.log("Server started on port " + PORT);
                AppLogger.info("Server started on port " + PORT);
            });

            loadGroupsFromDB();

            new Thread(this::acceptConnections).start();
        } catch (IOException e) {
            Platform.runLater(() -> chatServer.log("Error starting server: " + e.getMessage()));
            AppLogger.severe("Error starting server", e);
        }
    }

    private void acceptConnections() {
        while (isRunning) {
            try {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, chatServer, this);
                allHandlers.add(handler);
                new Thread(handler).start();
            } catch (IOException e) {
                if (isRunning) {
                    Platform.runLater(() -> {
                        chatServer.log("Error accepting connection: " + e.getMessage());
                        AppLogger.severe("Error accepting connection", e);
                    });
                }
            }
        }
    }

    private void loadGroupsFromDB() {
        // Load groups
        DBHelper.executeQuery("SELECT name, creator FROM groups", rs -> {
            try {
                int count = 0;
                while (rs.next()) {
                    String name = rs.getString(1);
                    String creator = rs.getString(2);
                    ChatGroup group = new ChatGroup(name, creator);
                    groups.put(name, group);
                    count++;
                }
                AppLogger.info("Total groups loaded from DB: " + count);
                Platform.runLater(() -> chatServer.updateGroupTable());
            } catch (Exception e) {
                AppLogger.severe("Error loading groups from DB", e);
            }
        });

        // Load group members
        DBHelper.executeQuery("SELECT group_name, username FROM group_members", rs -> {
            try {
                int count = 0;
                while (rs.next()) {
                    String groupName = rs.getString(1);
                    String username = rs.getString(2);
                    ChatGroup group = groups.get(groupName);
                    if (group != null) {
                        group.addMember(username);
                        count++;
                    }
                }
                AppLogger.info("Total group members loaded: " + count);
                Platform.runLater(() -> chatServer.updateGroupTable());
            } catch (Exception e) {
                AppLogger.severe("Error loading group members from DB", e);
            }
        });
    }

    public void stopServer() {
        isRunning = false;

        // Close all client handlers
        allHandlers.forEach(handler -> {
            try {
                handler.close();
            } catch (Exception e) {
                AppLogger.severe("Error closing client handler", e);
            }
        });
        allHandlers.clear();

        clients.clear();
        groups.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            AppLogger.severe("Error closing server socket", e);
        }

        Platform.runLater(() -> {
            chatServer.getControlPanel().setRunning(false);
            chatServer.updateClientTable();
            chatServer.updateGroupTable();
            chatServer.log("Server stopped");
            AppLogger.info("Server stopped");
        });
    }

    public void registerClient(String username, ClientHandler handler) {
        try {
            clients.put(username, handler);

            // Gửi danh sách users online
            handler.sendMessage("USER_LIST|" + String.join(",", clients.keySet()));

            // Gửi danh sách groups của user
            sendUserGroups(username, handler);

            // Thông báo user mới cho các client khác
            clients.values().stream()
                    .filter(c -> c != handler)
                    .forEach(c -> c.sendMessage("USER_JOINED|" + username));

            Platform.runLater(() -> {
                chatServer.getControlPanel().updateClientCount(clients.size());
                chatServer.updateClientTable();
                chatServer.log("Client registered: " + username);
                AppLogger.info("Client registered: " + username);
            });

            sendBroadcast(username + " joined the server!");

        } catch (Exception e) {
            AppLogger.severe("Error registering client: " + username, e);
        }
    }

    public void unregisterClient(String username) {
        try {
            // [FIX BUG ONGOING 2/4] Kiểm tra user có đang gọi video không, nếu có thì kết thúc ngay
            Long activeCallId = userActiveCalls.remove(username);
            if (activeCallId != null) {
                AppLogger.info("User " + username + " disconnected while in call " + activeCallId + ". Auto-ending call...");
                handleCallEnd(String.valueOf(activeCallId));
            }

            ClientHandler handler = clients.remove(username);
            if (handler != null) {
                allHandlers.remove(handler);
            }

            groups.values().forEach(g -> g.removeMember(username));

            clients.values().forEach(c -> c.sendMessage("USER_LEFT|" + username));

            Platform.runLater(() -> {
                chatServer.getControlPanel().updateClientCount(clients.size());
                chatServer.updateClientTable();
                chatServer.updateGroupTable();
                chatServer.log("Client unregistered: " + username);
                AppLogger.info("Client unregistered: " + username);
                chatServer.closePrivateChatTab(username);
            });

            sendBroadcast(username + " left the server.");

        } catch (Exception e) {
            AppLogger.severe("Error unregistering client: " + username, e);
        }
    }

    public void handleHistoryRequest(String requester, String type, String target) {
        String sql;
        if (type.equals("PRIVATE")) {
            sql = "SELECT sender, content, timestamp, type FROM messages " +
                    "WHERE type = 'PRIVATE' AND " +
                    "((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                    "ORDER BY timestamp ASC LIMIT 50";
        } else {
            sql = "SELECT sender, content, timestamp, type FROM messages " +
                    "WHERE type = 'GROUP' AND receiver = ? " +
                    "ORDER BY timestamp ASC LIMIT 50";
        }

        Object[] params = type.equals("PRIVATE")
                ? new Object[]{requester, target, target, requester}
                : new Object[]{target};

        DBHelper.executeQuery(sql, rs -> {
            try {
                while (rs.next()) {
                    String sender = rs.getString("sender");
                    String content = rs.getString("content");
                    String time = rs.getString("timestamp");
                    String safeContent = content.replace("|", "&#124;");

                    String response = "HISTORY_DATA|" + type + "|" + target + "|" + sender + "|" + safeContent + "|" + time;
                    sendToUser(requester, response);
                }
            } catch (Exception e) {
                AppLogger.severe("Error sending history to " + requester, e);
            }
        }, params);
    }

    public void handleFileRequest(String requester, String target) {
        String sql;
        boolean isPrivate = target.startsWith("PRIVATE_");

        if (isPrivate) {
            String otherUser = target.substring(8);
            sql = "SELECT filename, size, sender, sent_at FROM files " +
                    "WHERE ((sender = ? AND receiver = ?) OR (sender = ? AND receiver = ?)) " +
                    "ORDER BY sent_at ASC LIMIT 20";

            DBHelper.executeQuery(sql, rs -> {
                try {
                    while (rs.next()) {
                        String name = rs.getString("filename");
                        long size = rs.getLong("size");
                        String sender = rs.getString("sender");
                        String time = rs.getString("sent_at");

                        String resp = "FILES_DATA|" + target + "|" + name + "|" + size + "|" + sender + "|" + time;
                        sendToUser(requester, resp);
                    }
                } catch (Exception e) {
                    AppLogger.severe("Error sending files to " + requester, e);
                }
            }, requester, otherUser, otherUser, requester);

        } else {
            String groupName = target.substring(6);
            sql = "SELECT filename, size, sender, sent_at FROM files " +
                    "WHERE receiver = ? ORDER BY sent_at ASC LIMIT 20";

            DBHelper.executeQuery(sql, rs -> {
                try {
                    while (rs.next()) {
                        String name = rs.getString("filename");
                        long size = rs.getLong("size");
                        String sender = rs.getString("sender");
                        String time = rs.getString("sent_at");

                        String resp = "FILES_DATA|" + target + "|" + name + "|" + size + "|" + sender + "|" + time;
                        sendToUser(requester, resp);
                    }
                } catch (Exception e) {
                    AppLogger.severe("Error sending group files to " + requester, e);
                }
            }, groupName);
        }
    }

    public void handlePrivateMessage(String sender, String to, String msg) {
        try {
            String content = sender + ": " + msg;
            DBHelper.executeUpdate(
                    "INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('PRIVATE',?,?,?,NOW())",
                    sender, to, content
            );
            sendToUser(to, "PRIVATE|" + sender + "|" + content);
            Platform.runLater(() -> chatServer.log("[PRIVATE] " + sender + " -> " + to + ": " + msg));
            AppLogger.info("[PRIVATE] " + sender + " -> " + to + ": " + msg);
        } catch (Exception e) {
            AppLogger.severe("Error handling private message", e);
        }
    }

    public void handleBroadcast(String sender, String msg) {
        try {
            String fullMsg = "[SERVER]: " + msg;
            DBHelper.executeUpdate(
                    "INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('BROADCAST','SERVER','ALL',?,NOW())",
                    fullMsg
            );
            broadcast("BROADCAST|" + fullMsg);
            Platform.runLater(() -> {
                Message sysMsg = Message.createSystemMessage(fullMsg);
                if (chatServer.getChatPanels().containsKey("BROADCAST")) {
                    chatServer.getChatPanels().get("BROADCAST").appendMessage(sysMsg);
                }
                chatServer.log("[BROADCAST] " + msg);
            });
        } catch (Exception e) {
            AppLogger.severe("Error handling broadcast", e);
        }
    }

    public void handleGroupMessage(String sender, String group, String msg) {
        try {
            // --- BƯỚC 1: KIỂM TRA NGAY LẬP TỨC (Pre-Moderation) ---
            // Vì dùng Local Filter nên tốc độ cực nhanh, không lo lag server
            if (LocalFilterService.isContentViolated(msg)) {

                AppLogger.info("🚫 Blocked bad content from " + sender + ": " + msg);

                // Chỉ gửi cảnh báo riêng cho người gửi (các user khác trong nhóm sẽ KHÔNG biết gì cả)
                sendToUser(sender, "SYSTEM|🚫 Tin nhắn của bạn không được gửi đi do chứa từ khóa cấm!");

                // Dừng xử lý luôn (return), không lưu DB, không Broadcast
                return;
            }

            // --- BƯỚC 2: NẾU SẠCH SẼ THÌ XỬ LÝ BÌNH THƯỜNG ---
            String full = sender + ": " + msg;

            // Lưu tin nhắn vào DB
            String sql = "INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('GROUP',?,?,?,NOW())";
            DBHelper.executeUpdate(sql, sender, group, full);

            // Gửi cho mọi người
            sendToGroup(group, full);
            Platform.runLater(() -> chatServer.log("[GROUP] " + full));

        } catch (Exception e) {
            AppLogger.severe("Error handling group message", e);
        }
    }

    public void handleCreateGroup(String groupName, String creator, Set<String> members) {
        try {
            DBHelper.executeUpdate(
                    "INSERT INTO groups(name,creator) VALUES(?,?) ON DUPLICATE KEY UPDATE creator=creator",
                    groupName, creator
            );

            ChatGroup group = groups.computeIfAbsent(groupName, k -> new ChatGroup(groupName, creator));
            Set<String> allMembers = new HashSet<>(members);
            allMembers.add(creator);

            for (String member : allMembers) {
                if (group.addMember(member)) {
                    DBHelper.executeUpdate(
                            "INSERT INTO group_members(group_name,username) VALUES(?,?) ON DUPLICATE KEY UPDATE username=username",
                            groupName, member
                    );
                }
            }

            String joinedMsg = "GROUP_JOINED|" + groupName;
            for (String member : allMembers) {
                ClientHandler memberHandler = clients.get(member);
                if (memberHandler != null) {
                    memberHandler.sendMessage(joinedMsg);
                    sendUserGroups(member, memberHandler);
                }
            }

            Platform.runLater(() -> {
                chatServer.updateGroupTable();
                chatServer.log("Group created: " + groupName);
            });

        } catch (Exception e) {
            AppLogger.severe("Error creating group", e);
        }
    }

    public void handleJoinGroup(String username, String groupName) {
        try {
            ChatGroup group = groups.computeIfAbsent(groupName, k -> {
                DBHelper.executeUpdate("INSERT INTO groups(name,creator) VALUES(?,?) ON DUPLICATE KEY UPDATE creator=creator", groupName, "unknown");
                return new ChatGroup(groupName, "unknown");
            });

            if (group.addMember(username)) {
                DBHelper.executeUpdate("INSERT INTO group_members(group_name,username) VALUES(?,?) ON DUPLICATE KEY UPDATE username=username", groupName, username);
                ClientHandler handler = clients.get(username);
                if (handler != null) handler.sendMessage("GROUP_JOINED|" + groupName);
                sendToGroup(groupName, username + " joined the group.");
                Platform.runLater(() -> chatServer.updateGroupTable());
            }
        } catch (Exception e) {
            AppLogger.severe("Error joining group", e);
        }
    }

    public void handleLeaveGroup(String username, String groupName) {
        try {
            ChatGroup group = groups.get(groupName);
            if (group != null && group.removeMember(username)) {
                DBHelper.executeUpdate("DELETE FROM group_members WHERE group_name=? AND username=?", groupName, username);
                ClientHandler handler = clients.get(username);
                if (handler != null) handler.sendMessage("GROUP_LEFT|" + groupName);
                sendToGroup(groupName, username + " left the group.");
                Platform.runLater(() -> chatServer.updateGroupTable());
            }
        } catch (Exception e) {
            AppLogger.severe("Error leaving group", e);
        }
    }

    public void handleDissolveGroup(String groupName) {
        try {
            ChatGroup group = groups.remove(groupName);
            if (group != null) {
                DBHelper.executeUpdate("DELETE FROM group_members WHERE group_name=?", groupName);
                DBHelper.executeUpdate("DELETE FROM messages WHERE type='GROUP' AND receiver=?", groupName);
                DBHelper.executeUpdate("DELETE FROM groups WHERE name=?", groupName);

                group.getMembers().forEach(m -> {
                    ClientHandler c = clients.get(m);
                    if (c != null) c.sendMessage("GROUP_DISSOLVED|" + groupName);
                });

                Platform.runLater(() -> {
                    chatServer.updateGroupTable();
                    chatServer.log("Group dissolved: " + groupName);
                    chatServer.closeGroupChatTab(groupName);
                });
            }
        } catch (Exception e) {
            AppLogger.severe("Error dissolving group", e);
        }
    }

    public void sendBroadcast(String message) {
        try {
            String fullMsg = "[SERVER]: " + message;
            DBHelper.executeUpdate(
                    "INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('BROADCAST','SERVER','ALL',?,NOW())",
                    fullMsg
            );
            broadcast("BROADCAST|" + fullMsg);
            Platform.runLater(() -> {
                Message sysMsg = Message.createSystemMessage(fullMsg);
                if (chatServer.getChatPanels().containsKey("BROADCAST")) {
                    chatServer.getChatPanels().get("BROADCAST").appendMessage(sysMsg);
                }
                chatServer.log("Broadcast: " + message);
            });
        } catch (Exception e) {
            AppLogger.severe("Error sending broadcast", e);
        }
    }

    public void sendToGroup(String groupName, String message) {
        ChatGroup group = groups.get(groupName);
        if (group != null) {
            group.getMembers().forEach(m -> {
                ClientHandler c = clients.get(m);
                if (c != null) c.sendMessage("GROUP|" + groupName + "|" + message);
            });
            Platform.runLater(() -> {
                chatServer.appendToGroupChat(groupName, message);
                chatServer.log("[GROUP] " + message);
            });
        }
    }

    private void broadcast(String message) {
        clients.values().forEach(c -> c.sendMessage(message));
    }

    public void sendToUser(String username, String message) {
        ClientHandler c = clients.get(username);
        if (c != null) c.sendMessage(message);
    }

    public Map<String, ClientHandler> getClients() {
        return clients;
    }

    public Map<String, ChatGroup> getGroups() {
        return groups;
    }

    public void processMessage(String username, String message) {
        try {
            String[] parts = message.split("\\|", -1);
            if (parts.length == 0) return;
            String command = parts[0];

            switch (command) {
                case "BROADCAST" -> {
                    if (parts.length > 1) handleBroadcast(username, parts[1]);
                }
                case "PRIVATE" -> {
                    if (parts.length >= 3) handlePrivateMessage(username, parts[1], parts[2]);
                }
                case "GROUP" -> {
                    if (parts.length >= 3) handleGroupMessage(username, parts[1], parts[2]);
                }
                case "CREATE_GROUP" -> {
                    if (parts.length >= 2) {
                        String groupName = parts[1];
                        Set<String> members = new HashSet<>();
                        if (parts.length >= 3 && !parts[2].isEmpty()) {
                            String[] memberArray = parts[2].split(",");
                            for (String member : memberArray)
                                if (!member.trim().isEmpty()) members.add(member.trim());
                        }
                        handleCreateGroup(groupName, username, members);
                    }
                }
                case "JOIN_GROUP" -> {
                    if (parts.length >= 2) handleJoinGroup(username, parts[1]);
                }
                case "LEAVE_GROUP" -> {
                    if (parts.length >= 2) handleLeaveGroup(username, parts[1]);
                }
                case "DISSOLVE_GROUP" -> {
                    if (parts.length >= 2) handleDissolveGroup(parts[1]);
                }
                case "FILE_PRIVATE" -> {
                    if (parts.length >= 6) {
                        handleFilePrivate(username, parts[1], parts[3], Long.parseLong(parts[4]), parts[5]);
                    }
                }
                case "FILE_GROUP" -> {
                    if (parts.length >= 6) {
                        handleFileGroup(username, parts[1], parts[3], Long.parseLong(parts[4]), parts[5]);
                    }
                }
                case "EMOJI_PRIVATE" -> {
                    if (parts.length >= 3) handleEmojiPrivate(username, parts[1], parts[2]);
                }
                case "EMOJI_GROUP" -> {
                    if (parts.length >= 3) handleEmojiGroup(username, parts[1], parts[2]);
                }
                case "WEBRTC" -> {
                    String[] webrtcParts = message.split("\\|", 3);
                    if (webrtcParts.length >= 3) {
                        String targetUser = webrtcParts[1];
                        String jsonPayload = webrtcParts[2];
                        handleWebRTC(username, targetUser, jsonPayload);
                    }
                }
                case "CALL_REQ" -> {
                    if (parts.length >= 3) {
                        handleCallRequest(username, parts[1], parts[2]);
                    }
                }
                case "CALL_RES" -> {
                    if (parts.length >= 4) {
                        handleCallResponse(username, parts[1], parts[2], Long.parseLong(parts[3]));
                    }
                }
                case "CALL_END" -> {
                    if (parts.length >= 3) {
                        handleCallEnd(parts[2]);
                        ClientHandler target = getClients().get(parts[1]);
                        if (target != null) target.sendMessage("CALL_END|" + username);
                    }
                }
                case "TYPING" -> {
                    // Cấu trúc: TYPING | Target | STATE
                    if (parts.length >= 3) {
                        String target = parts[1];
                        String state = parts[2];
                        handleTyping(username, target, state);
                    }
                }
                case "VOICE_PRIVATE" -> {
                    // Packet: VOICE_PRIVATE | Receiver | FileName | Size | Type
                    if (parts.length >= 5) {
                        String receiver = parts[1];
                        String fileName = parts[2];
                        long size = Long.parseLong(parts[3]);
                        String fileType = parts[4];
                        handleVoiceMessage(username, receiver, fileName, size, fileType, true);
                    }
                }
                case "VOICE_GROUP" -> {
                    // Packet: VOICE_GROUP | GroupName | FileName | Size | Type
                    if (parts.length >= 5) {
                        String group = parts[1];
                        String fileName = parts[2];
                        long size = Long.parseLong(parts[3]);
                        String fileType = parts[4];
                        handleVoiceMessage(username, group, fileName, size, fileType, false);
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.severe("Error processing message from " + username, e);
        }
    }

    private void handleTyping(String sender, String target, String state) {
        String packet = "TYPING|" + sender + "|" + target + "|" + state;

        // Kiểm tra xem target là User hay Group
        if (groups.containsKey(target)) {
            // Là Group -> Gửi cho cả nhóm (trừ người gửi)
            ChatGroup g = groups.get(target);
            g.getMembers().stream()
                    .filter(m -> !m.equals(sender))
                    .forEach(m -> sendToUser(m, packet));
        } else {
            // Là Private -> Gửi cho người đó
            sendToUser(target, packet);
        }
    }

    private void sendUserGroups(String username, ClientHandler handler) {
        Set<String> userGroups = new HashSet<>();
        for (ChatGroup group : groups.values()) {
            if (group.getMembers().contains(username)) {
                userGroups.add(group.name);
            }
        }
        if (!userGroups.isEmpty()) {
            handler.sendMessage("GROUP_LIST|" + String.join(",", userGroups));
        }
    }

    public void handleEmojiPrivate(String sender, String to, String emojiCode) {
        DBHelper.executeUpdate("INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('EMOJI',?,?,?,NOW())", sender, to, emojiCode);
        sendToUser(to, "EMOJI_PRIVATE|" + sender + "|" + to + "|" + emojiCode);
        Platform.runLater(() -> chatServer.log("[EMOJI] " + sender + " -> " + to));
    }

    public void handleEmojiGroup(String sender, String groupName, String emojiCode) {
        DBHelper.executeUpdate("INSERT INTO messages(type,sender,receiver,content,timestamp) VALUES('EMOJI',?,?,?,NOW())", sender, groupName, emojiCode);
        sendToGroup(groupName, "EMOJI_GROUP|" + sender + "|" + groupName + "|" + emojiCode);
        Platform.runLater(() -> chatServer.log("[EMOJI_GROUP] " + sender + " -> " + groupName));
    }

    public void handleFilePrivate(String sender, String to, String fileName, long fileSize, String fileType) {
        DBHelper.executeUpdate("INSERT INTO files(filename, sender, receiver, size, file_type, sent_at) VALUES(?,?,?,?,?,NOW())", fileName, sender, to, fileSize, fileType);
        if (!sender.equals(to)) {
            sendToUser(to, "FILE_PRIVATE|" + sender + "|" + to + "|" + fileName + "|" + fileSize + "|" + fileType);
        }
        Platform.runLater(() -> chatServer.log("[FILE] " + sender + " -> " + to + ": " + fileName));
    }

    public void handleFileGroup(String sender, String groupName, String fileName, long fileSize, String fileType) {
        DBHelper.executeUpdate("INSERT INTO files(filename, sender, receiver, size, file_type, sent_at) VALUES(?,?,?,?,?,NOW())", fileName, sender, groupName, fileSize, fileType);
        ChatGroup group = groups.get(groupName);
        if (group != null) {
            group.getMembers().stream().filter(m -> !m.equals(sender)).forEach(m -> {
                ClientHandler c = clients.get(m);
                if (c != null)
                    c.sendMessage("FILE_GROUP|" + sender + "|" + groupName + "|" + fileName + "|" + fileSize + "|" + fileType);
            });
        }
        Platform.runLater(() -> chatServer.log("[FILE_GROUP] " + sender + " -> " + groupName + ": " + fileName));
    }

    public void handleWebRTC(String sender, String target, String jsonPayload) {
        ClientHandler targetClient = clients.get(target);
        if (targetClient != null) {
            targetClient.sendMessage("WEBRTC|" + sender + "|" + jsonPayload);
        } else {
            ClientHandler senderClient = clients.get(sender);
            if (senderClient != null) {
                String errorJson = "{\"type\":\"ERROR\",\"message\":\"User is offline\"}";
                senderClient.sendMessage("WEBRTC|SERVER|" + errorJson);
            }
        }
    }

    private void handleCallRequest(String caller, String callee, String type) {
        String sql = "INSERT INTO calls (caller, callee, call_type, start_time, status) VALUES (?, ?, ?, NOW(), 'ONGOING')";
        long callId = DBHelper.executeInsertAndGetId(sql, caller, callee, type);

        callStartTimes.put(callId, System.currentTimeMillis());

        ClientHandler targetClient = clients.get(callee);
        if (targetClient != null) {
            targetClient.sendMessage("CALL_REQ|" + caller + "|" + callId + "|" + type);
        } else {
            DBHelper.executeUpdate("UPDATE calls SET status = 'MISSED', end_time = NOW() WHERE id = ?", callId);
            sendCallReport(caller, callee, "MISSED", 0);
        }
    }

    private void handleCallResponse(String responder, String caller, String status, long callId) {
        if ("REJECTED".equals(status)) {
            DBHelper.executeUpdate("UPDATE calls SET status = 'REJECTED', end_time = NOW() WHERE id = ?", callId);
            sendCallReport(caller, responder, "REJECTED", 0);
            callStartTimes.remove(callId);
        } else if ("ACCEPTED".equals(status)) {
            // [FIX BUG ONGOING 3/4] Lưu lại ID cuộc gọi đang diễn ra của 2 user
            userActiveCalls.put(caller, callId);
            userActiveCalls.put(responder, callId);
        }

        ClientHandler callerClient = clients.get(caller);
        if (callerClient != null) {
            callerClient.sendMessage("CALL_RES|" + responder + "|" + status + "|" + callId);
        }
    }

    private void handleCallEnd(String callIdStr) {
        try {
            long callId = Long.parseLong(callIdStr);

            if (!callStartTimes.containsKey(callId)) {
                return;
            }

            long endTime = System.currentTimeMillis();
            long startTime = callStartTimes.getOrDefault(callId, endTime);
            long durationMillis = endTime - startTime;
            long durationSeconds = durationMillis / 1000;

            callStartTimes.remove(callId);

            // [FIX BUG ONGOING 4/4] Xóa trạng thái bận của các user liên quan
            userActiveCalls.values().removeIf(id -> id == callId);

            String sql = "UPDATE calls SET end_time = NOW(), status = 'COMPLETED', duration = ? WHERE id = ?";
            DBHelper.executeUpdate(sql, durationSeconds, callId);

            DBHelper.executeQuery("SELECT caller, callee FROM calls WHERE id = ?", rs -> {
                try {
                    if (rs.next()) {
                        String caller = rs.getString("caller");
                        String callee = rs.getString("callee");
                        sendCallReport(caller, callee, "ENDED", durationSeconds);

                        // Gửi tín hiệu ngắt cưỡng chế (Server Force End) cho cả 2 bên
                        // Đề phòng trường hợp một bên tắt máy, bên kia vẫn đợi
                        ClientHandler c1 = clients.get(caller);
                        ClientHandler c2 = clients.get(callee);
                        if (c1 != null) c1.sendMessage("CALL_END|SERVER");
                        if (c2 != null) c2.sendMessage("CALL_END|SERVER");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, callId);

        } catch (NumberFormatException e) {
            AppLogger.severe("Invalid Call ID format: " + callIdStr);
        }
    }

    private void sendCallReport(String caller, String callee, String status, long durationSec) {
        long msgTimeMillis = System.currentTimeMillis();
        if ("ENDED".equals(status)) {
            msgTimeMillis -= (durationSec * 1000);
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String sqlTimestamp = sdf.format(new java.util.Date(msgTimeMillis));

        String content = status + "|" + durationSec;

        try {
            String sql = "INSERT INTO messages(type, sender, receiver, content, timestamp) VALUES ('PRIVATE', ?, ?, ?, ?)";
            DBHelper.executeUpdate(sql, caller, callee, content, sqlTimestamp);
        } catch (Exception e) {
            AppLogger.severe("Error saving call log", e);
        }

        String packet = "CALL_LOG|" + caller + "|" + callee + "|" + content + "|" + sqlTimestamp;

        ClientHandler c1 = clients.get(caller);
        ClientHandler c2 = clients.get(callee);

        if (c1 != null) c1.sendMessage(packet);
        if (c2 != null) c2.sendMessage(packet);
    }

    public void sendServerBroadcast(String message) {
        String safeMessage = message.replace("|", "&#124;");

        String fullMsg = "[SERVER]: " + safeMessage;

        broadcast("BROADCAST|" + fullMsg);
    }

    public void handleVoiceMessage(String sender, String target, String fileName, long size, String fileType, boolean isPrivate) {
        // 1. Lưu metadata file vào bảng files (để sau này tải)
        String sqlFile = "INSERT INTO files(filename, sender, receiver, size, file_type, sent_at) VALUES(?,?,?,?,?,NOW())";
        DBHelper.executeUpdate(sqlFile, fileName, sender, target, size, fileType);

        // 2. Lưu tin nhắn vào bảng messages với type = 'VOICE'
        String content = fileName; // Nội dung tin nhắn Voice chính là tên file
        String sqlType = isPrivate ? "PRIVATE" : "GROUP";

        String sqlMsg = "INSERT INTO messages(type, sender, receiver, content, timestamp) VALUES('VOICE', ?, ?, ?, NOW())";
        DBHelper.executeUpdate(sqlMsg, sender, target, content);

        // 3. Gửi cho người nhận
        String packet = (isPrivate ? "VOICE_PRIVATE" : "VOICE_GROUP") + "|" + sender + "|" + target + "|" + fileName + "|" + size + "|" + fileType;

        if (isPrivate) {
            sendToUser(target, packet);
        } else {
            sendToGroup(target, packet);
        }

        // Log
        Platform.runLater(() -> chatServer.log("[VOICE] " + sender + " -> " + target));
    }

    private void initSystemUser() {
        DBHelper.executeQuery("SELECT 1 FROM users WHERE username = 'SERVER'", rs -> {
            try {
                if (!rs.next()) {
                    // Nếu chưa có user SERVER thì tạo mới để tránh lỗi Foreign Key
                    DBHelper.executeUpdate(
                            "INSERT INTO users (username, password, online, last_seen) VALUES ('SERVER', 'SYSTEM', 1, NOW())"
                    );
                    AppLogger.info("✅ System user 'SERVER' created automatically.");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}