package com.chatapp.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private MessageType type;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
    private String metadata;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Double uploadProgress;
    private MessageStatus status;

    // --- CONSTRUCTORS ---

    // Constructor đầy đủ
    public Message(MessageType type, String content, String sender, LocalDateTime timestamp,
                   String metadata, String fileName, Long fileSize, String fileType,
                   Double uploadProgress, MessageStatus status) {
        this.type = type;
        this.content = content;
        this.sender = sender;
        this.timestamp = timestamp;
        this.metadata = metadata;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.uploadProgress = uploadProgress;
        this.status = status;
    }

    // Constructor đơn giản (Dùng nhiều nhất)
    public Message(MessageType type, String content, String sender, LocalDateTime timestamp) {
        this(type, content, sender, timestamp, null, null, null, null, 1.0, MessageStatus.SENT);
    }

    // Constructor có metadata
    public Message(MessageType type, String content, String sender, LocalDateTime timestamp, String metadata) {
        this(type, content, sender, timestamp, metadata, null, null, null, 1.0, MessageStatus.SENT);
    }

    // --- FACTORY METHODS (Phương thức tạo nhanh) ---

    public static Message createTextMessage(String content, String sender) {
        return new Message(MessageType.TEXT, content, sender, LocalDateTime.now());
    }

    public static Message createFileMessage(String fileName, Long fileSize, String fileType, String sender) {
        String content = "File: " + fileName;
        return new Message(MessageType.FILE, content, sender, LocalDateTime.now(),
                null, fileName, fileSize, fileType, 1.0, MessageStatus.SENT);
    }

    // Thêm Factory cho Voice để tiện sử dụng
    public static Message createVoiceMessage(String fileName, String sender) {
        return new Message(MessageType.VOICE, fileName, sender, LocalDateTime.now(),
                null, fileName, 0L, "wav", 1.0, MessageStatus.SENT);
    }

    public static Message createFileUploadMessage(String fileName, Long fileSize, String fileType,
                                                  String sender, Double progress) {
        String content = "Uploading: " + fileName;
        return new Message(MessageType.FILE, content, sender, LocalDateTime.now(),
                null, fileName, fileSize, fileType, progress, MessageStatus.SENDING);
    }

    public static Message createSystemMessage(String content) {
        return new Message(MessageType.SYSTEM, content, "SYSTEM", LocalDateTime.now());
    }

    public static Message createEmojiMessage(String emojiCode, String sender) {
        String content = "Sent an emoji: " + emojiCode;
        return new Message(MessageType.EMOJI, content, sender, LocalDateTime.now(),
                emojiCode, null, null, null, 1.0, MessageStatus.SENT);
    }

    public static Message createCallMessage(String content, String sender, LocalDateTime timestamp) {
        return new Message(MessageType.CALL, content, sender, timestamp);
    }

    // Method withTimestamp (giữ nguyên logic cũ nhưng trả về object mới hoặc this đều được)
    public Message withTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    // --- ENUM ---
    public enum MessageStatus {
        SENDING, SENT, DELIVERED, READ, FAILED
    }

    // --- GETTERS ---
    public MessageType getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getMetadata() {
        return metadata;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public Double getUploadProgress() {
        return uploadProgress;
    }

    public MessageStatus getStatus() {
        return status;
    }

    // --- SETTERS (QUAN TRỌNG: CẦN CÓ ĐỂ SỬA LỖI CONTROLLER) ---
    public void setType(MessageType type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    } // <--- Controller cần hàm này

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setUploadProgress(Double uploadProgress) {
        this.uploadProgress = uploadProgress;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    // --- HELPER METHODS ---

    public String getEmojiCode() {
        return metadata;
    }

    // Setter giả lập cho Emoji (lưu vào metadata)
    public void setEmojiCode(String emojiCode) {
        this.metadata = emojiCode;
    }

    public String getFormattedTime() {
        if (timestamp == null) return "";
        return timestamp.toLocalTime().withNano(0).toString();
    }

    public String getFormattedDate() {
        if (timestamp == null) return "";
        return timestamp.toLocalDate().toString();
    }

    public String getFormattedFileSize() {
        if (fileSize == null) return "";
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    public String getFileIcon() {
        if (fileType == null && fileName != null && fileName.contains(".")) {
            // Tự động đoán fileType từ fileName nếu null
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1);
            return getIconByExt(ext);
        } else if (fileType != null) {
            return getIconByExt(fileType);
        }
        return "📎";
    }

    private String getIconByExt(String ext) {
        switch (ext.toLowerCase()) {
            case "pdf":
                return "📄";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                return "🖼️";
            case "doc":
            case "docx":
                return "📝";
            case "xls":
            case "xlsx":
                return "📊";
            case "zip":
            case "rar":
            case "7z":
                return "📦";
            case "mp3":
            case "wav":
            case "flac":
                return "🎵";
            case "mp4":
            case "avi":
            case "mkv":
                return "🎬";
            default:
                return "📎";
        }
    }
}