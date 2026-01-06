package com.chatapp.server;

import com.chatapp.common.AppLogger;
import com.chatapp.common.DBHelper;
import com.chatapp.common.PasswordHasher;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

class ClientHandler implements Runnable {
    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private final ChatServer server;
    private final ServerNetworkManager networkManager;
    private boolean isAuthenticated = false;

    // Thư mục chứa file upload trên Server
    private static final String UPLOAD_DIR = "uploads/";

    public ClientHandler(Socket socket, ChatServer server, ServerNetworkManager networkManager) {
        this.socket = socket;
        this.server = server;
        this.networkManager = networkManager;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8), true);

            // === VÒNG LẶP XÁC THỰC ===
            while (!isAuthenticated) {
                String request = in.readLine();
                if (request == null) return;

                String[] parts = request.split("\\|", 3);
                String command = parts[0];

                if ("LOGIN".equals(command) && parts.length == 3) {
                    handleLogin(parts[1], parts[2]);
                } else if ("REGISTER".equals(command) && parts.length == 3) {
                    handleRegister(parts[1], parts[2]);
                } else if ("RESET_PASSWORD".equals(command) && parts.length == 3) {
                    handleResetPassword(parts[1], parts[2]);
                } else {
                    out.println("ERROR|Vui lòng đăng nhập trước!");
                }
            }

            // === VÒNG LẶP XỬ LÝ TIN NHẮN CHÍNH ===
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMsg = message;
                String[] parts = finalMsg.split("\\|", -1);

                if (parts.length > 0) {
                    String command = parts[0];

                    // --- XỬ LÝ LỊCH SỬ ---
                    if ("REQ_HISTORY".equals(command) && parts.length >= 3) {
                        String type = parts[1];
                        String target = parts[2];
                        Platform.runLater(() -> networkManager.handleHistoryRequest(username, type, target));
                        continue;
                    } else if ("REQ_FILES".equals(command) && parts.length >= 2) {
                        String target = parts[1];
                        Platform.runLater(() -> networkManager.handleFileRequest(username, target));
                        continue;
                    }
//                    else if ("REQ_DOWNLOAD_FILE".equals(command) && parts.length >= 2) {
//                        String fileName = parts[1];
//                        // Xử lý trên luồng riêng để không chặn Chat
//                        new Thread(() -> handleFileDownload(fileName)).start();
//                        continue;
//                    }
                }

                // Các tin nhắn chat thông thường
                Platform.runLater(() -> networkManager.processMessage(username, finalMsg));
            }

        } catch (IOException e) {
            AppLogger.warning("Connection error with " + username + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

//    private void handleFileDownload(String displayFileName) {
//        // Biến để chứa tên file thật trên ổ cứng (có timestamp)
//        final String[] realPathOnDisk = {null};
//
//        // BƯỚC 1: Truy vấn Database để tìm tên file thật dựa trên tên hiển thị
//        // Lấy file mới nhất (ORDER BY id DESC) nếu có nhiều file trùng tên
//        String sql = "SELECT path FROM files WHERE filename = ? ORDER BY id DESC LIMIT 1";
//
//        DBHelper.executeQuery(sql, rs -> {
//            try {
//                if (rs.next()) {
//                    realPathOnDisk[0] = rs.getString("path");
//                }
//            } catch (SQLException e) {
//                AppLogger.severe("DB Error searching file: " + displayFileName, e);
//            }
//        }, displayFileName);
//
//        // Nếu không tìm thấy trong DB thì thử dùng chính tên client gửi lên (fallback)
//        String targetFileName = (realPathOnDisk[0] != null && !realPathOnDisk[0].isEmpty())
//                ? realPathOnDisk[0]
//                : displayFileName;
//
//        File file = new File(UPLOAD_DIR + targetFileName);
//
//        // BƯỚC 2: Kiểm tra file có tồn tại vật lý không
//        if (!file.exists()) {
//            out.println("DOWNLOAD_ERROR|File không tồn tại trên hệ thống server.");
//            AppLogger.warning("User " + username + " requested missing file: " + targetFileName + " (Display: " + displayFileName + ")");
//            return;
//        }
//
//        try {
//            // BƯỚC 3: Đọc và gửi file
//            byte[] fileContent = Files.readAllBytes(file.toPath());
//            String encodedString = Base64.getEncoder().encodeToString(fileContent);
//
//            // Gửi về Client: Giữ nguyên tên hiển thị (displayFileName) để Client lưu cho đẹp
//            out.println("FILE_CONTENT|" + displayFileName + "|" + encodedString);
//
//            AppLogger.info("Sent file " + targetFileName + " as " + displayFileName + " to " + username);
//
//        } catch (IOException e) {
//            out.println("DOWNLOAD_ERROR|Lỗi khi đọc file trên server.");
//            AppLogger.severe("Error sending file: " + targetFileName, e);
//        }
//    }

    private void handleLogin(String user, String pass) {
        if (networkManager.getClients().containsKey(user)) {
            out.println("LOGIN_FAIL|Tài khoản đang được đăng nhập ở nơi khác!");
            return;
        }
        AtomicBoolean loginSuccess = new AtomicBoolean(false);
        DBHelper.executeQuery("SELECT password FROM users WHERE username = ?", rs -> {
            try {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    if (PasswordHasher.verifyPassword(pass, storedHash)) loginSuccess.set(true);
                }
            } catch (SQLException e) {
                AppLogger.severe("Login DB error", e);
            }
        }, user);

        if (loginSuccess.get()) {
            this.username = user;
            this.isAuthenticated = true;
            out.println("LOGIN_SUCCESS|" + user);
            DBHelper.updateUserOnlineStatus(username, true);
            Platform.runLater(() -> networkManager.registerClient(username, this));
        } else {
            out.println("LOGIN_FAIL|Sai tên đăng nhập hoặc mật khẩu!");
        }
    }

    private void handleRegister(String user, String pass) {
        if (user.length() < 3 || pass.length() < 4) {
            out.println("REGISTER_FAIL|Tên hoặc mật khẩu quá ngắn!");
            return;
        }
        AtomicBoolean exists = new AtomicBoolean(false);
        DBHelper.executeQuery("SELECT 1 FROM users WHERE username = ?", rs -> {
            try {
                if (rs.next()) exists.set(true);
            } catch (Exception e) {
            }
        }, user);
        if (exists.get()) {
            out.println("REGISTER_FAIL|Tên đăng nhập đã tồn tại!");
            return;
        }
        try {
            String hash = PasswordHasher.hashPassword(pass);
            DBHelper.executeUpdate("INSERT INTO users (username, password, online, last_seen) VALUES (?, ?, 0, NOW())", user, hash);
            out.println("REGISTER_SUCCESS|Đăng ký thành công! Hãy đăng nhập.");
        } catch (Exception e) {
            out.println("REGISTER_FAIL|Lỗi hệ thống khi đăng ký.");
        }
    }

    private void handleResetPassword(String user, String newPass) {
        final boolean[] exists = {false};
        DBHelper.executeQuery("SELECT 1 FROM users WHERE username = ?", rs -> {
            try {
                if (rs.next()) exists[0] = true;
            } catch (Exception e) {
            }
        }, user);
        if (exists[0]) {
            try {
                String newHash = PasswordHasher.hashPassword(newPass);
                DBHelper.executeUpdate("UPDATE users SET password = ? WHERE username = ?", newHash, user);
                out.println("RESET_SUCCESS|Mật khẩu đã được thay đổi thành công!");
            } catch (Exception e) {
                out.println("RESET_FAIL|Lỗi hệ thống.");
            }
        } else {
            out.println("RESET_FAIL|Tài khoản không tồn tại!");
        }
    }

    private void cleanup() {
        if (isAuthenticated && username != null) {
            Platform.runLater(() -> {
                networkManager.unregisterClient(username);
                server.log("User disconnected: " + username);
            });
            try {
                DBHelper.updateUserOnlineStatus(username, false);
            } catch (Exception e) {
            }
        }
        close();
    }

    public void sendMessage(String msg) {
        if (out != null) out.println(msg);
    }

    public Socket getSocket() {
        return socket;
    }

    public void close() {
        try {
            if (in != null) in.close();
        } catch (Exception e) {
        }
        try {
            if (out != null) out.close();
        } catch (Exception e) {
        }
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
        }
    }
}