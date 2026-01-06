package com.chatapp.client;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;

public class AESUtil {

    private static final String ALGORITHM = "AES";

    private static SecretKeySpec getKey(String password) throws Exception {
        byte[] key = password.getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        // Chỉ lấy 16 byte đầu tiên cho AES-128 (để tương thích rộng rãi)
        key = Arrays.copyOf(key, 16);
        return new SecretKeySpec(key, ALGORITHM);
    }

    // --- MÃ HÓA FILE ---
    public static File encryptFile(File inputFile, String password) throws Exception {
        // Tạo file đầu ra có đuôi .enc
        File outputFile = new File(inputFile.getParent(), inputFile.getName() + ".enc");

        SecretKeySpec secretKey = getKey(password);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                cos.write(buffer, 0, read);
            }
        }
        return outputFile;
    }

    // --- GIẢI MÃ FILE ---
    public static File decryptFile(File inputFile, String password) throws Exception {
        // Tên file gốc (Bỏ đuôi .enc)
        String outputName = inputFile.getName().replace(".enc", "");
        // Để tránh ghi đè file gốc nếu có, ta thêm prefix "decrypted_" hoặc lưu chỗ khác
        // Ở đây ta lưu cùng thư mục
        File outputFile = new File(inputFile.getParent(), outputName);

        SecretKeySpec secretKey = getKey(password);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);

        try (FileInputStream fis = new FileInputStream(inputFile);
             CipherInputStream cis = new CipherInputStream(fis, cipher);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = cis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        return outputFile;
    }
}