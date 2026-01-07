package com.chatapp.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtils {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lấy thời gian hiện tại của hệ thống dưới dạng chuỗi theo định dạng "HH:mm:ss".
     */
    public static String getCurrentTime() {
        return LocalDateTime.now().format(TIME_FORMATTER);
    }

    /**
     * Lấy ngày và giờ hiện tại của hệ thống dưới dạng chuỗi theo định dạng "yyyy-MM-dd HH:mm:ss".
     */
    public static String getCurrentDateTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    /**
     * Chuyển đổi một đối tượng LocalDateTime cụ thể thành chuỗi theo định dạng chuẩn "yyyy-MM-dd HH:mm:ss".
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}