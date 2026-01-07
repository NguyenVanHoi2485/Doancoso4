package com.chatapp.common;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class MessageUtils {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, MMM dd");

    /**
     * Định dạng hiển thị thời gian của tin nhắn dựa trên khoảng cách so với hiện tại
     * (Hôm nay, Hôm qua, trong tuần, hoặc ngày cụ thể) theo múi giờ Việt Nam.
     */
    public static String formatMessageTime(LocalDateTime timestamp) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime time = timestamp.atZone(zone).toLocalDateTime();

        if (time.toLocalDate().equals(now.toLocalDate())) {
            return "Today " + time.format(TIME_FORMATTER);
        } else if (time.toLocalDate().equals(now.minusDays(1).toLocalDate())) {
            return "Yesterday " + time.format(TIME_FORMATTER);
        } else if (ChronoUnit.DAYS.between(time.toLocalDate(), now.toLocalDate()) <= 6) {
            return time.format(DateTimeFormatter.ofPattern("EEEE HH:mm"));
        } else {
            return time.format(DATE_FORMATTER) + " " + time.format(TIME_FORMATTER);
        }
    }

    /**
     * Xác định xem có cần hiển thị thanh phân cách ngày giữa tin nhắn hiện tại và tin nhắn trước đó hay không
     * (trả về true nếu hai tin nhắn thuộc hai ngày khác nhau).
     */
    public static boolean shouldShowDateSeparator(LocalDateTime current, LocalDateTime previous) {
        if (previous == null) return true;

        // QUAN TRỌNG: Dùng cùng múi giờ để so sánh ngày
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        return !current.atZone(zone).toLocalDate().equals(previous.atZone(zone).toLocalDate());
    }

    /**
     * Tạo chuỗi văn bản hiển thị trên thanh phân cách ngày (VD: "Today", "Yesterday" hoặc ngày tháng đầy đủ).
     */
    public static String getDateSeparatorText(LocalDateTime date) {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime target = date.atZone(zone).toLocalDateTime();

        if (target.toLocalDate().equals(now.toLocalDate())) {
            return "Today";
        } else if (target.toLocalDate().equals(now.minusDays(1).toLocalDate())) {
            return "Yesterday";
        } else {
            return target.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"));
        }
    }
}