package com.chatapp.common;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordHasher {

    /**
     * Băm mật khẩu dạng văn bản thô sử dụng thuật toán BCrypt với độ phức tạp (cost) là 12 để lưu trữ an toàn.
     */
    public static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

    /**
     * Kiểm tra tính hợp lệ của mật khẩu bằng cách so sánh mật khẩu nhập vào với chuỗi băm (hash) đã lưu trữ.
     * Trả về true nếu khớp, ngược lại trả về false.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);
            return result.verified;
        } catch (Exception e) {
            AppLogger.warning("Invalid hash format: " + e.getMessage());
            return false;
        }
    }
}