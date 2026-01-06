package com.chatapp.server;

import com.chatapp.common.AppLogger;
import com.chatapp.common.DBHelper;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class FileTransferServer {
    private static final int FILE_PORT = 5556;
    private static final String UPLOAD_DIR = "uploads/";

    public static void start() {
        new Thread(() -> {
            try {
                // Tạo thư mục uploads nếu chưa tồn tại
                new File(UPLOAD_DIR).mkdirs();

                ServerSocket serverSocket = new ServerSocket(FILE_PORT);
                AppLogger.info("File Transfer Server started on port " + FILE_PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new FileHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                AppLogger.severe("Error starting File Transfer Server", e);
            }
        }).start();
    }

    static class FileHandler implements Runnable {
        private final Socket socket;

        public FileHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream())) {
                String sender = in.readUTF();
                String fileName = in.readUTF();
                long fileSize = in.readLong();

                // Tạo tên file duy nhất để tránh trùng lặp
                String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
                File outputFile = new File(UPLOAD_DIR + uniqueFileName);

                // Lưu file
                FileOutputStream fileOut = new FileOutputStream(outputFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalRead = 0;

                while (totalRead < fileSize && (bytesRead = in.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }

                fileOut.close();

                AppLogger.info("File saved: " + uniqueFileName + " from " + sender +
                        " | Original: " + fileName + " | Size: " + fileSize + " bytes");

                DBHelper.executeUpdate(
                        "UPDATE files SET path = ? WHERE filename = ? AND sender = ? ORDER BY sent_at DESC LIMIT 1",
                        uniqueFileName, fileName, sender
                );

            } catch (Exception e) {
                AppLogger.severe("Error handling file transfer", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static void startDownloadServer() {
        new Thread(() -> {
            try {
                ServerSocket downloadSocket = new ServerSocket(5557);
                AppLogger.info("✅ File Download Server started on port 5557");

                while (true) {
                    Socket clientSocket = downloadSocket.accept();
                    AppLogger.info("📥 Download connection accepted from: " + clientSocket.getInetAddress());
                    new Thread(new DownloadHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                AppLogger.severe("❌ Error starting Download Server", e);
            }
        }).start();
    }

    static class DownloadHandler implements Runnable {
        private final Socket socket;

        public DownloadHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (DataInputStream in = new DataInputStream(socket.getInputStream());
                 DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

                String command = in.readUTF();
                if ("DOWNLOAD".equals(command)) {
                    String requestedFileName = in.readUTF(); // Tên file hiển thị (VD: TaiLieu.pdf)

                    // [FIX] TRA CỨU DB ĐỂ LẤY TÊN FILE THẬT TRÊN Ổ CỨNG (VD: 123456_TaiLieu.pdf)
                    final String[] realPath = {null};
                    String sql = "SELECT path FROM files WHERE filename = ? ORDER BY id DESC LIMIT 1";

                    DBHelper.executeQuery(sql, rs -> {
                        try {
                            if (rs.next()) {
                                realPath[0] = rs.getString("path");
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, requestedFileName);

                    // Nếu tìm thấy trong DB thì dùng, không thì fallback về tên gốc
                    String fileNameOnDisk = (realPath[0] != null) ? realPath[0] : requestedFileName;

                    File fileToSend = new File(UPLOAD_DIR + fileNameOnDisk);

                    if (fileToSend.exists() && fileToSend.isFile()) {
                        out.writeBoolean(true); // Báo Client: Có file
                        out.writeLong(fileToSend.length()); // Báo kích thước

                        try (FileInputStream fileIn = new FileInputStream(fileToSend)) {
                            byte[] buffer = new byte[8192]; // Tăng buffer lên 8KB cho nhanh
                            int bytesRead;
                            while ((bytesRead = fileIn.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                            }
                        }
                        AppLogger.info("Sent file: " + fileNameOnDisk);
                    } else {
                        out.writeBoolean(false); // Báo Client: Không tìm thấy
                        AppLogger.warning("File not found on disk: " + fileNameOnDisk);
                    }
                }
            } catch (Exception e) {
                AppLogger.severe("Error handling download request", e);
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
}