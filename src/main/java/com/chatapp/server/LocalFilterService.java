package com.chatapp.server;

import com.chatapp.common.AppLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class LocalFilterService {

    // Dùng Set để tìm kiếm nhanh
    private static final Set<String> BAD_WORDS = new HashSet<>();

    // Block khởi tạo tĩnh: Tự động chạy khi Server bật
    static {
        loadBadWords();
    }

    private static void loadBadWords() {
        try (InputStream is = LocalFilterService.class.getResourceAsStream("/bad_words.txt")) {
            if (is == null) {
                AppLogger.warning("⚠️ Không tìm thấy file bad_words.txt!");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String word = line.trim().toLowerCase();
                if (!word.isEmpty()) {
                    BAD_WORDS.add(word);
                    // Thêm cả phiên bản không dấu để bắt chặt hơn
                    BAD_WORDS.add(removeAccents(word));
                }
            }
            AppLogger.info("✅ Local Filter Loaded: " + BAD_WORDS.size() + " keywords.");
        } catch (Exception e) {
            AppLogger.severe("Lỗi khi load bộ lọc từ khóa", e);
        }
    }

    /**
     * Hàm chính để kiểm tra nội dung
     */
    public static boolean isContentViolated(String content) {
        if (content == null || content.isEmpty()) return false;

        // 1. Chuyển về chữ thường + Xóa dấu tiếng Việt
        String normalized = removeAccents(content.toLowerCase());

        // 2. Kiểm tra từng từ cấm
        for (String badWord : BAD_WORDS) {
            // Dùng contains để bắt cả "zzzznguzzz"
            if (normalized.contains(badWord)) {
                AppLogger.info("🚫 Bắt được từ cấm: " + badWord + " trong tin nhắn: " + content);
                return true;
            }
        }
        return false;
    }

    // Hàm tiện ích: Xóa dấu tiếng Việt (Chết -> Chet)
    private static String removeAccents(String s) {
        String temp = Normalizer.normalize(s, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }
}