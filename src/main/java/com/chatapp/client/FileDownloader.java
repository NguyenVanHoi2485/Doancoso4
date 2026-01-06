package com.chatapp.client;

import com.chatapp.common.AppLogger;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.*;
import java.net.Socket;

public class FileDownloader {

    private static final int DOWNLOAD_PORT = 5557; // Port dành riêng cho tải file

    public static void download(String serverIp, String fileName, ChatClient chatClient) {
        // Chạy trên luồng riêng để không làm đơ giao diện
        new Thread(() -> {
            Socket socket = null;
            try {
                socket = new Socket(serverIp, DOWNLOAD_PORT);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                // 1. Gửi yêu cầu tải file
                out.writeUTF("DOWNLOAD");
                out.writeUTF(fileName);
                out.flush();

                // 2. Nhận phản hồi từ Server
                boolean exists = in.readBoolean();
                if (!exists) {
                    Platform.runLater(() -> chatClient.showAlert("File không tồn tại trên server!"));
                    return;
                }

                long fileSize = in.readLong();

                // 3. Chuẩn bị file lưu trên Client
                File downloadDir = new File("client_downloads");
                if (!downloadDir.exists()) downloadDir.mkdirs();

                String saveName = fileName;

                File destFile = new File(downloadDir, saveName);

                // 4. Bắt đầu nhận luồng dữ liệu (Stream)
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    byte[] buffer = new byte[8192]; // Buffer 8KB
                    long totalRead = 0;
                    int bytesRead;

                    // Đọc đến khi hết file (dựa vào fileSize)
                    while (totalRead < fileSize && (bytesRead = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;

                        // (Tùy chọn) Có thể cập nhật Progress Bar tại đây nếu muốn
                    }
                }

                AppLogger.info("Downloaded file: " + destFile.getAbsolutePath());

                // 5. Thông báo thành công trên UI
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION,
                            "Tải xong!\nFile lưu tại: " + destFile.getAbsolutePath(), ButtonType.OK);
                    alert.initOwner(chatClient.getPrimaryStage());
                    alert.show();
                });

            } catch (IOException e) {
                AppLogger.severe("Download failed", e);
                Platform.runLater(() -> chatClient.showAlert("Lỗi khi tải file: " + e.getMessage()));
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}