package com.chatapp.common;

import java.util.logging.*;

public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("ChatApp");

    /**
     * Khối khởi tạo tĩnh (Static Initializer) để thiết lập cấu hình cho Logger ngay khi class được nạp.
     * Cấu hình bao gồm: bỏ handler mặc định, tạo định dạng log tùy chỉnh (thời gian, level, nội dung) và xuất ra console.
     */
    static {
        try {
            // Remove default handlers
            LOGGER.setUseParentHandlers(false);

            // Create console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter() {
                private static final String FORMAT = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

                @Override
                public synchronized String format(LogRecord record) {
                    return String.format(FORMAT,
                            new java.util.Date(record.getMillis()),
                            record.getLevel().getLocalizedName(),
                            record.getMessage()
                    );
                }
            });
            LOGGER.addHandler(consoleHandler);

            // Set level
            LOGGER.setLevel(Level.ALL);
            consoleHandler.setLevel(Level.ALL);

        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    /**
     * Ghi lại nhật ký hệ thống ở mức thông tin (INFO) - dùng cho các thông báo hoạt động bình thường.
     */
    public static void info(String message) {
        LOGGER.info(message);
    }

    /**
     * Ghi lại nhật ký hệ thống ở mức cảnh báo (WARNING) - dùng cho các vấn đề tiềm ẩn cần lưu ý.
     */
    public static void warning(String message) {
        LOGGER.warning(message);
    }

    /**
     * Ghi lại nhật ký hệ thống ở mức lỗi nghiêm trọng (SEVERE) - dùng khi xảy ra lỗi xử lý.
     */
    public static void severe(String message) {
        LOGGER.severe(message);
    }

    /**
     * Ghi lại nhật ký hệ thống ở mức lỗi nghiêm trọng kèm theo chi tiết ngoại lệ (StackTrace).
     */
    public static void severe(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
}