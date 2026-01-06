package com.chatapp.common;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordHasher {

    public static String hashPassword(String password) {
        return BCrypt.withDefaults().hashToString(12, password.toCharArray());
    }

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