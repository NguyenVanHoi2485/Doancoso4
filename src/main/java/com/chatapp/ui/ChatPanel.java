package com.chatapp.ui;

import com.chatapp.client.AESUtil;
import com.chatapp.client.NetworkManager;
import com.chatapp.common.MessageUtils;
import com.chatapp.model.Message;
import com.chatapp.model.MessageType;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.chatapp.client.AudioRecorder;

public class ChatPanel extends StackPane {

    // Logic Chat
    private final ListView<Message> messageList;
    private final ObservableList<Message> messages;
    private final TextField messageField;
    private final Button sendButton;
    private final Button fileButton;
    private final Button emojiButton;

    // --- KHAI BÁO THÊM BIẾN CHO VOICE ---
    private final Button micButton;
    private boolean isRecording = false;
    private AudioRecorder recorder;

    // Logic Callback & Network
    private Consumer<String> sendAction;
    private TriConsumer<String, Long, String> sendFileAction;
    private final NetworkManager networkManager;
    private final String currentTargetName;

    // Logic Call ID & State
    private long currentCallId = -1;
    private boolean isInCall = false;
    private Button btnVideo;
    private Label statusLbl;

    // Logic Bridge
    private final AtomicInteger callbackPort = new AtomicInteger(0);
    private ServerSocket callbackSocket;

    // Layout Components
    private final BorderPane chatLayout;
    private final HBox inputBox;
    private Consumer<String> downloadAction;

    // Logic Typing
    private PauseTransition typingTimer;
    private boolean isTyping = false;
    private Label typingLabel;

    /**
     * Khởi tạo giao diện Chat Panel, bao gồm danh sách tin nhắn, khu vực nhập liệu, các nút chức năng và xử lý sự kiện.
     */
    public ChatPanel(String title, boolean enabled, NetworkManager networkManager) {
        this.networkManager = networkManager;
        this.currentTargetName = title;

        chatLayout = new BorderPane();
        chatLayout.setStyle("-fx-background-color: #f0f2f5;"); // Màu nền Messenger

        // HEADER
        chatLayout.setTop(createHeader(title));

        // --- KHU VỰC LIST MESSAGE ---
        messages = FXCollections.observableArrayList();
        messageList = new ListView<>(messages);
        messageList.setCellFactory(lv -> new MessageCell(this));

        // CSS để ẩn viền và làm trong suốt nền cho List
        messageList.setStyle("-fx-background-color: transparent; -fx-border-width: 0;");

        // Label hiển thị "User đang nhập..."
        typingLabel = new Label();
        typingLabel.setStyle("-fx-text-fill: #888; -fx-font-style: italic; -fx-font-size: 11px; -fx-padding: 2 0 5 15;");
        typingLabel.setVisible(false);
        // Cho typingLabel chiều cao = 0 khi ẩn để không chiếm chỗ
        typingLabel.managedProperty().bind(typingLabel.visibleProperty());

        VBox centerBox = new VBox(messageList, typingLabel);

        // Đẩy List xuống hết cỡ để fix khoảng trống
        VBox.setVgrow(messageList, Priority.ALWAYS);

        chatLayout.setCenter(centerBox);

        // --- INPUT AREA ---
        inputBox = new HBox(10);
        inputBox.setPadding(new Insets(10, 15, 10, 15));
        inputBox.setAlignment(Pos.CENTER_LEFT);
        inputBox.getStyleClass().add("input-container");

        fileButton = createIconButton("📎", "Gửi file");
        fileButton.setOnAction(e -> chooseAndSendFile());

        micButton = createIconButton("🎤", "Ghi âm giọng nói");
        micButton.setOnAction(e -> handleMicToggle());

        emojiButton = createIconButton("😀", "Chèn emoji");
        emojiButton.setOnAction(e -> showEmojiPicker());

        messageField = new TextField();
        messageField.setPromptText("Nhập tin nhắn...");
        messageField.setDisable(!enabled);
        messageField.getStyleClass().add("message-field");
        messageField.setOnAction(e -> sendMessage());
        HBox.setHgrow(messageField, Priority.ALWAYS);

        // Logic Typing Timer
        typingTimer = new PauseTransition(Duration.seconds(2));
        typingTimer.setOnFinished(e -> {
            isTyping = false;
            if (networkManager != null)
                networkManager.sendTyping(currentTargetName, false); // Gửi STOP
        });

        messageField.setOnKeyPressed(e -> {
            typingTimer.playFromStart(); // Reset bộ đếm mỗi khi gõ
            if (!isTyping) {
                isTyping = true;
                if (networkManager != null)
                    networkManager.sendTyping(currentTargetName, true); // Gửi START
            }
        });

        sendButton = new Button("➤");
        sendButton.getStyleClass().add("send-button");
        sendButton.setDisable(!enabled);
        sendButton.setOnAction(e -> sendMessage());

        // Đã sửa thứ tự thêm nút
        inputBox.getChildren().addAll(fileButton, micButton, emojiButton, messageField, sendButton);
        chatLayout.setBottom(inputBox);

        this.getChildren().addAll(chatLayout);

        // Ẩn Input nếu là Broadcast
        if (title.contains("📢") || "BROADCAST".equals(title)) {
            hideInputArea();
        }
    }

    /**
     * Tạo phần đầu (Header) của khung chat chứa Avatar, Tên người dùng/nhóm và Nút gọi Video.
     */
    private HBox createHeader(String title) {
        HBox hbox = new HBox(15);
        hbox.setPadding(new Insets(15, 20, 15, 20));
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setStyle("-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 0, 5, 10, 0);");

        Circle avatar = new Circle(20);
        avatar.setStroke(Color.WHITESMOKE);
        avatar.setStrokeWidth(1);
        try {
            String avatarUrl = "https://ui-avatars.com/api/?background=random&name=" + title.replace(" ", "+");
            Image img = new Image(avatarUrl, true);
            avatar.setFill(new ImagePattern(img));
        } catch (Exception e) {
            avatar.setFill(Color.LIGHTBLUE);
        }

        VBox info = new VBox(3);
        Label nameLbl = new Label(title);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #050505;");

        statusLbl = new Label("Active now");
        statusLbl.setStyle("-fx-text-fill: #2ecc71; -fx-font-size: 11px;");

        info.getChildren().addAll(nameLbl, statusLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnVideo = new Button("📹");
        updateCallButtonUI();
        btnVideo.setOnAction(e -> handleVideoBtnClick());

        if (title.contains("📢") || "BROADCAST".equals(title)) {
            btnVideo.setVisible(false);
            btnVideo.setManaged(false);
            statusLbl.setText("Server Broadcast");
        }

        hbox.getChildren().addAll(avatar, info, spacer, btnVideo);
        return hbox;
    }

    /**
     * Xử lý sự kiện khi nhấn nút Video Call (Gọi hoặc Kết thúc cuộc gọi).
     */
    private void handleVideoBtnClick() {
        if (isInCall) triggerEndCall();
        else if (networkManager != null) {
            networkManager.getOut().println("CALL_REQ|" + currentTargetName + "|VIDEO");
            setStatusText("Đang gọi...", "#f39c12");
        }
    }

    /**
     * Cập nhật văn bản và màu sắc cho nhãn trạng thái (Status Label).
     */
    private void setStatusText(String text, String color) {
        Platform.runLater(() -> {
            if (statusLbl != null) {
                statusLbl.setText(text);
                statusLbl.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 10px;");
            }
        });
    }

    /**
     * Cập nhật giao diện nút gọi (Icon, Tooltip, Style) dựa trên trạng thái cuộc gọi.
     */
    private void updateCallButtonUI() {
        if (isInCall) {
            btnVideo.setText("⏹");
            btnVideo.setTooltip(new Tooltip("Kết thúc cuộc gọi"));
            btnVideo.setStyle("-fx-background-color: #ff4d4d; -fx-text-fill: white; -fx-font-size: 18px; -fx-cursor: hand; -fx-background-radius: 5px;");
        } else {
            btnVideo.setText("📹");
            btnVideo.setTooltip(new Tooltip("Video Call"));
            btnVideo.setStyle("-fx-background-color: transparent; -fx-font-size: 18px; -fx-cursor: hand; -fx-text-fill: #0084ff;");
        }
    }

    /**
     * Khởi tạo ServerSocket cục bộ để nhận tín hiệu kết thúc từ trang HTML Video Call.
     */
    private void startCallbackServer() {
        new Thread(() -> {
            try {
                callbackSocket = new ServerSocket(0);
                callbackPort.set(callbackSocket.getLocalPort());
                while (!callbackSocket.isClosed()) {
                    Socket socket = callbackSocket.accept();
                    Platform.runLater(this::triggerEndCall);
                    socket.close();
                }
            } catch (Exception e) {
            }
        }).start();
    }

    /**
     * Đóng ServerSocket cục bộ.
     */
    private void stopCallbackServer() {
        try {
            if (callbackSocket != null && !callbackSocket.isClosed()) callbackSocket.close();
        } catch (Exception e) {
        }
    }

    /**
     * Thiết lập trạng thái khi cuộc gọi bắt đầu.
     */
    public void setCallStarted(long callId) {
        Platform.runLater(() -> {
            this.currentCallId = callId;
            this.isInCall = true;
            updateCallButtonUI();
            setStatusText("Đang trong cuộc gọi", "#e74c3c");
        });
    }

    /**
     * Thiết lập trạng thái khi cuộc gọi kết thúc và reset giao diện.
     */
    public void setCallEnded() {
        Platform.runLater(() -> {
            this.currentCallId = -1;
            this.isInCall = false;
            updateCallButtonUI();
            stopCallbackServer();
            setStatusText("Online", "#2ecc71");
        });
    }

    /**
     * Gửi yêu cầu kết thúc cuộc gọi tới Server.
     */
    public void triggerEndCall() {
        if (currentCallId != -1) {
            networkManager.getOut().println("CALL_END|" + currentTargetName + "|" + currentCallId);
        }
        setCallEnded();
    }

    /**
     * Bắt đầu cuộc gọi Video Call bằng cách tạo file HTML tạm thời và mở trình duyệt mặc định.
     */
    public void startExternalVideoCall(boolean isCaller) {
        try {
            startCallbackServer();
            Thread.sleep(100);
            String myName = networkManager.getMyUsername();
            String targetName = currentTargetName;
            String roomId = (myName.compareTo(targetName) < 0) ? myName + "_" + targetName : targetName + "_" + myName;
            String targetUserString = isCaller ? targetName : "";

            String projectPath = System.getProperty("user.dir");
            File originalFile = new File(projectPath, "src/main/resources/web/video_call.html");
            if (!originalFile.exists()) {
                showAlert("Không tìm thấy file gốc: " + originalFile.getAbsolutePath());
                return;
            }

            byte[] encoded = java.nio.file.Files.readAllBytes(originalFile.toPath());
            String htmlContent = new String(encoded, StandardCharsets.UTF_8);

            htmlContent = htmlContent.replace("__ROOM_ID__", roomId);
            htmlContent = htmlContent.replace("__USER_ID__", myName);
            htmlContent = htmlContent.replace("__TARGET_USER__", targetUserString);
            htmlContent = htmlContent.replace("__CALLBACK_PORT__", String.valueOf(callbackPort.get()));

            String tempFileName = "video_call_" + myName + ".html";
            File tempFile = new File(projectPath, tempFileName);
            java.nio.file.Files.write(tempFile.toPath(), htmlContent.getBytes(StandardCharsets.UTF_8));

            if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(tempFile);
            else showAlert("Không hỗ trợ mở trình duyệt!");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Lỗi khởi tạo Video: " + e.getMessage());
        }
    }

    /**
     * Xử lý tín hiệu đến (Phương thức giữ chỗ cho mở rộng sau này).
     */
    public void handleIncomingSignal(String signal) {
    }

    /**
     * Thiết lập ID cho cuộc gọi hiện tại.
     */
    public void setCallId(long id) {
        this.currentCallId = id;
    }

    /**
     * Lấy nội dung từ ô nhập liệu và gửi tin nhắn văn bản đi.
     */
    private void sendMessage() {
        if (sendAction != null) {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                sendAction.accept(msg);
                messageField.clear();
                messageField.requestFocus();
            }
        }
    }

    /**
     * Mở hộp thoại chọn file, xử lý logic mã hóa (nếu chọn) và gửi file.
     */
    private void chooseAndSendFile() {
        if (networkManager == null) {
            showAlert("Chưa kết nối!");
            return;
        }
        FileChooser fc = new FileChooser();
        File f = fc.showOpenDialog(getScene().getWindow());

        if (f != null && sendFileAction != null) {
            // --- HỎI MÃ HÓA ---
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Bảo mật file");
            alert.setHeaderText("Bạn có muốn mã hóa file này không?");
            alert.setContentText("Nếu chọn CÓ, bạn sẽ cần đặt mật khẩu.");

            ButtonType btnYes = new ButtonType("Có (Mã hóa)");
            ButtonType btnNo = new ButtonType("Không (Gửi thường)");
            ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnYes, btnNo, btnCancel);

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == btnYes) {
                // --- XỬ LÝ GỬI FILE MÃ HÓA ---
                TextInputDialog passDialog = new TextInputDialog();
                passDialog.setTitle("Đặt mật khẩu");
                passDialog.setHeaderText("Nhập mật khẩu để khóa file:");
                passDialog.setContentText("Mật khẩu:");

                passDialog.showAndWait().ifPresent(password -> {
                    if (password.isEmpty()) {
                        showAlert("Mật khẩu không được để trống!");
                        return;
                    }

                    try {
                        // 1. Mã hóa file ra file tạm (.enc)
                        File encryptedFile = AESUtil.encryptFile(f, password);

                        // 2. Gửi file .enc đi
                        sendFileAction.accept(encryptedFile.getName(), encryptedFile.length(), "enc"); // fileType là 'enc'
                        sendActualFile(encryptedFile, encryptedFile.getName());

                        encryptedFile.deleteOnExit();

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        showAlert("Lỗi mã hóa: " + ex.getMessage());
                    }
                });

            } else if (result.isPresent() && result.get() == btnNo) {
                // --- GỬI THƯỜNG (Logic cũ) ---
                sendFileAction.accept(f.getName(), f.length(), getFileExtension(f.getName()));
                sendActualFile(f, f.getName());
            }
        }
    }

    /**
     * Gửi dữ liệu file thực tế qua Socket phụ (cổng 5556).
     */
    private void sendActualFile(File file, String fileName) {
        new Thread(() -> {
            try (Socket s = new Socket("localhost", 5556);
                 DataOutputStream dos = new DataOutputStream(s.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {

                dos.writeUTF(networkManager.getMyUsername());
                dos.writeUTF(fileName);
                dos.writeLong(file.length());
                byte[] buf = new byte[4096];
                int read;
                while ((read = fis.read(buf)) != -1) dos.write(buf, 0, read);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Hiển thị bảng chọn Emoji.
     */
    private void showEmojiPicker() {
        EmojiPicker.showEmojiPicker(getScene().getWindow(), messageField::appendText);
    }

    /**
     * Thêm tin nhắn mới vào danh sách và sắp xếp lại theo thời gian.
     */
    public void addMessageAndSort(Message m) {
        Platform.runLater(() -> {
            messages.add(m);
            FXCollections.sort(messages, Comparator.comparing(Message::getTimestamp));
            scrollToBottom();
        });
    }

    /**
     * Thêm tin nhắn vào cuối danh sách.
     */
    public void appendMessage(Message m) {
        Platform.runLater(() -> {
            messages.add(m);
            scrollToBottom();
        });
    }

    /**
     * Xóa toàn bộ tin nhắn trên giao diện.
     */
    public void clearMessages() {
        Platform.runLater(messages::clear);
    }

    /**
     * Cuộn danh sách tin nhắn xuống dưới cùng.
     */
    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (!messages.isEmpty()) {
                messageList.scrollTo(messages.size() - 1);
            }
        });
    }

    /**
     * Tạo nút bấm có biểu tượng và tooltip.
     */
    private Button createIconButton(String icon, String tooltip) {
        Button btn = new Button(icon);
        btn.getStyleClass().add("icon-button");
        btn.setTooltip(new Tooltip(tooltip));
        return btn;
    }

    /**
     * Lấy phần mở rộng (đuôi file) từ tên file.
     */
    private String getFileExtension(String f) {
        int i = f.lastIndexOf('.');
        return i == -1 ? "unknown" : f.substring(i + 1);
    }

    /**
     * Hiển thị thông báo lỗi dạng Popup.
     */
    private void showAlert(String m) {
        new Alert(Alert.AlertType.ERROR, m, ButtonType.OK).show();
    }

    /**
     * Đăng ký callback xử lý khi gửi tin nhắn văn bản.
     */
    public void setSendAction(Consumer<String> a) {
        this.sendAction = a;
    }

    /**
     * Đăng ký callback xử lý khi gửi file.
     */
    public void setSendFileAction(TriConsumer<String, Long, String> a) {
        this.sendFileAction = a;
    }

    /**
     * Bật/Tắt khả năng nhập liệu và gửi tin.
     */
    public void setInputEnabled(boolean e) {
        messageField.setDisable(!e);
        sendButton.setDisable(!e);
    }

    /**
     * Ẩn các nút chức năng đa phương tiện (File, Emoji...).
     */
    public void disableMediaButtons() {
        fileButton.setVisible(false);
        emojiButton.setVisible(false);
    }

    /**
     * Ẩn toàn bộ khu vực nhập liệu (Dùng cho Broadcast).
     */
    public void hideInputArea() {
        inputBox.setVisible(false);
        inputBox.setManaged(false);
    }

    /**
     * Đăng ký callback xử lý khi tải file.
     */
    public void setDownloadAction(Consumer<String> action) {
        this.downloadAction = action;
    }

    /**
     * Kích hoạt hành động tải file.
     */
    public void downloadFile(String fileName, File saveDest) {
        if (downloadAction != null) {
            downloadAction.accept(fileName);
            System.out.println("DEBUG: ChatPanel requesting download for: " + fileName);
        }
    }

    /**
     * Hiển thị hoặc ẩn trạng thái "Đang nhập..." của đối phương.
     */
    public void showTyping(String who, boolean typing) {
        Platform.runLater(() -> {
            if (typing) {
                typingLabel.setText(who + " đang nhập...");
                typingLabel.setVisible(true);
            } else {
                typingLabel.setVisible(false);
            }
        });
    }

    /**
     * Xử lý logic nút ghi âm: Bắt đầu ghi hoặc Dừng và Gửi.
     */
    private void handleMicToggle() {
        if (networkManager == null) return;

        if (!isRecording) {
            // --- BẮT ĐẦU GHI ÂM ---
            isRecording = true;
            recorder = new AudioRecorder();

            String tempName = "voice_" + System.currentTimeMillis() + ".wav";
            recorder.startRecording(tempName);

            micButton.setText("⏹"); // Icon Stop
            micButton.setStyle("-fx-text-fill: red; -fx-background-color: transparent; -fx-font-size: 20px;");
            messageField.setPromptText("Đang ghi âm... (Nhấn Stop để gửi)");
            messageField.setDisable(true);

        } else {
            // --- DỪNG VÀ GỬI ---
            isRecording = false;
            File voiceFile = recorder.stopRecording();

            micButton.setText("🎤");
            micButton.setStyle("");
            micButton.getStyleClass().add("icon-button");
            messageField.setPromptText("Nhập tin nhắn...");
            messageField.setDisable(false);

            if (voiceFile != null && voiceFile.exists()) {
                sendActualFile(voiceFile, voiceFile.getName());

                String cmdType = currentTargetName.startsWith("GROUP") ? "VOICE_GROUP" : "VOICE_PRIVATE";
                String target = currentTargetName.startsWith("GROUP") ? currentTargetName : currentTargetName;

                String packet = cmdType + "|" + target + "|" + voiceFile.getName() + "|" + voiceFile.length() + "|wav";
                networkManager.sendMessage(packet);
            }
        }
    }

    private static class MessageCell extends ListCell<Message> {
        private final ChatPanel parent;
        private final VBox rootBox = new VBox(5);
        private final HBox bubbleContainer = new HBox();
        private final VBox bubble = new VBox(3);
        private final Label senderName = new Label();
        private final Label timestamp = new Label();
        private final Label dateSeparator = new Label();

        private final Circle miniAvatar = new Circle(14);

        /**
         * Khởi tạo Cell hiển thị tin nhắn tùy chỉnh.
         */
        public MessageCell(ChatPanel parent) {
            this.parent = parent;
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            setStyle("-fx-background-color: transparent;");

            dateSeparator.setMaxWidth(Double.MAX_VALUE);
            dateSeparator.setAlignment(Pos.CENTER);
            dateSeparator.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-padding: 10 0 5 0; -fx-font-weight: bold;");

            timestamp.getStyleClass().add("msg-meta");
        }

        @Override
        /**
         * Cập nhật nội dung hiển thị cho từng loại tin nhắn (Text, Emoji, File, Call, Voice).
         */
        protected void updateItem(Message msg, boolean empty) {
            super.updateItem(msg, empty);
            if (empty || msg == null) {
                setGraphic(null);
                return;
            }
            rootBox.getChildren().clear();
            bubble.getChildren().clear();
            bubbleContainer.getChildren().clear();

            int index = getIndex();
            Message prevMsg = index > 0 && index < getListView().getItems().size() ? getListView().getItems().get(index - 1) : null;
            if (MessageUtils.shouldShowDateSeparator(msg.getTimestamp(), prevMsg != null ? prevMsg.getTimestamp() : null)) {
                dateSeparator.setText(MessageUtils.getDateSeparatorText(msg.getTimestamp()));
                rootBox.getChildren().add(dateSeparator);
            }

            boolean isMe = false;
            String myName = (parent.networkManager != null) ? parent.networkManager.getMyUsername() : "";

            if (msg.getType() == MessageType.SYSTEM || "SERVER".equals(msg.getSender())) {
                Label sysLabel = new Label(msg.getContent());
                sysLabel.setStyle("-fx-background-color: #e4e6eb; -fx-text-fill: #65676b; -fx-padding: 5 10; -fx-background-radius: 10; -fx-font-size: 11px;");
                HBox sysBox = new HBox(sysLabel);
                sysBox.setAlignment(Pos.CENTER);
                rootBox.getChildren().add(sysBox);
                setGraphic(rootBox);
                return;
            }

            if (msg.getSender() != null && msg.getSender().equals(myName)) isMe = true;

            if (!isMe) {
                String avatarUrl = "https://ui-avatars.com/api/?background=random&name=" + msg.getSender();
                try {
                    miniAvatar.setFill(new ImagePattern(new Image(avatarUrl, true)));
                } catch (Exception e) {
                    miniAvatar.setFill(Color.LIGHTGRAY);
                }
                HBox.setMargin(miniAvatar, new Insets(0, 8, 0, 0));
            }

            bubble.getStyleClass().clear();
            bubble.getStyleClass().add("msg-bubble");
            if (isMe) {
                bubble.getStyleClass().add("msg-sent");
                bubbleContainer.setAlignment(Pos.CENTER_RIGHT);
                timestamp.setStyle("-fx-text-fill: rgba(255,255,255,0.7);");
            } else {
                bubble.getStyleClass().add("msg-received");
                bubbleContainer.setAlignment(Pos.CENTER_LEFT);
                timestamp.setStyle("-fx-text-fill: #65676b;");
            }

            if (!isMe && parent.currentTargetName.startsWith("GROUP")) {
                senderName.setText(msg.getSender());
                senderName.setStyle("-fx-font-size: 11px; -fx-text-fill: #65676b; -fx-font-weight: bold; -fx-padding: 0 0 2 0;");
                bubble.getChildren().add(senderName);
            }

            switch (msg.getType()) {
                case TEXT:
                    Text t = new Text(msg.getContent());
                    t.getStyleClass().add("text");
                    t.setWrappingWidth(Math.min(parent.getScene().getWidth() * 0.6, 400));
                    bubble.getChildren().add(new TextFlow(t));
                    break;
                case EMOJI:
                    Label e = new Label(msg.getEmojiCode());
                    e.setStyle("-fx-font-size: 32px; -fx-background-color: transparent;");
                    bubble.setStyle("-fx-background-color: transparent; -fx-effect: null;");
                    bubble.getChildren().add(e);
                    break;
                case FILE:
                    String fileName = msg.getFileName();
                    String lowerName = fileName.toLowerCase();
                    boolean isEncrypted = lowerName.endsWith(".enc"); // Nhận diện file mã hóa

                    VBox fileBox = new VBox(5);
                    fileBox.setStyle("-fx-padding: 5;");

                    if (isEncrypted) {
                        // --- GIAO DIỆN FILE MÃ HÓA ---
                        StackPane lockPane = new StackPane();
                        lockPane.setPrefSize(200, 60);
                        lockPane.setStyle("-fx-background-color: #ffebee; -fx-background-radius: 10; -fx-border-color: #ffcdd2; -fx-border-radius: 10; -fx-cursor: hand;");

                        Label iconLock = new Label("🔒");
                        iconLock.setStyle("-fx-font-size: 24px;");

                        Label lblInfo = new Label("File bảo mật\n" + fileName);
                        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #c62828; -fx-font-weight: bold;");
                        lblInfo.setWrapText(true);
                        lblInfo.setMaxWidth(160);

                        HBox hb = new HBox(10, iconLock, lblInfo);
                        hb.setAlignment(Pos.CENTER_LEFT);
                        hb.setPadding(new Insets(5, 10, 5, 10));

                        lockPane.getChildren().add(hb);

                        // Xử lý khi bấm vào file mã hóa
                        lockPane.setOnMouseClicked(ev -> {
                            File downloadDir = new File("client_downloads");
                            File encFile = new File(downloadDir, fileName);

                            if (encFile.exists()) {
                                // Đã tải về -> Hỏi mật khẩu để giải mã
                                TextInputDialog passDialog = new TextInputDialog();
                                passDialog.setTitle("Giải mã file");
                                passDialog.setHeaderText("File này bị khóa.");
                                passDialog.setContentText("Nhập mật khẩu để mở:");

                                passDialog.showAndWait().ifPresent(password -> {
                                    try {
                                        // Giải mã
                                        File decryptedFile = AESUtil.decryptFile(encFile, password);

                                        // Mở file đã giải mã
                                        if (java.awt.Desktop.isDesktopSupported()) {
                                            java.awt.Desktop.getDesktop().open(decryptedFile);
                                        } else {
                                            parent.showAlert("Đã giải mã: " + decryptedFile.getName());
                                        }
                                    } catch (Exception ex) {
                                        parent.showAlert("Sai mật khẩu hoặc lỗi giải mã!");
                                        ex.printStackTrace();
                                    }
                                });
                            } else {
                                // Chưa tải -> Tải về trước
                                lblInfo.setText("Đang tải xuống...");
                                parent.downloadFile(fileName, null);
                                // (Logic tự động refresh icon bạn đã làm ở bước trước có thể áp dụng lại ở đây nếu muốn)
                            }
                        });

                        fileBox.getChildren().add(lockPane);

                    } else {
                        // --- LOGIC CŨ CHO ẢNH VÀ FILE THƯỜNG ---
                        boolean isImage = lowerName.endsWith(".jpg") || lowerName.endsWith(".png") ||
                                lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif");

                        if (isImage) {
                            File downloadDir = new File("client_downloads");
                            File downloadedFile = new File(downloadDir, fileName);

                            ImageView imageView = new ImageView();
                            imageView.setFitWidth(200);
                            imageView.setPreserveRatio(true);
                            imageView.setSmooth(true);

                            if (downloadedFile.exists()) {
                                try {
                                    Image img = new Image(downloadedFile.toURI().toString(), 250, 0, true, true);
                                    imageView.setImage(img);
                                    fileBox.getChildren().add(imageView);
                                } catch (Exception exLoad) {
                                    fileBox.getChildren().add(new Label("Lỗi ảnh"));
                                }
                            } else {
                                StackPane placeholder = new StackPane();
                                placeholder.setPrefSize(200, 150);
                                placeholder.setStyle("-fx-background-color: #eee; -fx-background-radius: 10; -fx-cursor: hand;");
                                Label lbl = new Label("🖼️ " + fileName + "\n(Nhấn để xem)");
                                lbl.setWrapText(true);
                                placeholder.getChildren().add(lbl);

                                placeholder.setOnMouseClicked(ev -> {
                                    lbl.setText("Đang tải...");
                                    parent.downloadFile(fileName, null);

                                    // Logic tự động reload ảnh
                                    new Thread(() -> {
                                        try {
                                            for (int i = 0; i < 10; i++) {
                                                Thread.sleep(500);
                                                if (downloadedFile.exists() && downloadedFile.length() > 0) {
                                                    Platform.runLater(() -> {
                                                        try {
                                                            fileBox.getChildren().clear();
                                                            Image img = new Image(downloadedFile.toURI().toString(), 250, 0, true, true);
                                                            imageView.setImage(img);
                                                            fileBox.getChildren().add(imageView);
                                                        } catch (Exception exUI) {
                                                        }
                                                    });
                                                    break;
                                                }
                                            }
                                        } catch (Exception ie) {
                                        }
                                    }).start();
                                });
                                fileBox.getChildren().add(placeholder);
                            }
                        } else {
                            // File thường khác
                            VBox fb = new VBox(5);
                            fb.getStyleClass().add("file-box");
                            Label fn = new Label(msg.getFileName());
                            fn.setStyle("-fx-font-weight: bold;");
                            Button db = new Button("⬇ Tải");
                            db.setOnAction(ev -> parent.downloadFile(msg.getFileName(), null));
                            fb.getChildren().addAll(fn, db);
                            fileBox.getChildren().add(fb);
                        }
                    }

                    bubble.getChildren().add(fileBox);
                    break;

                case CALL:
                    VBox callBox = new VBox(5);
                    callBox.setPadding(new Insets(10));
                    callBox.setStyle("-fx-background-color: rgba(0, 0, 0, 0.05); -fx-background-radius: 10; -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 10;");
                    String[] parts = msg.getContent().split("\\|");
                    String status = parts.length > 0 ? parts[0] : "UNKNOWN";
                    long duration = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                    Label iconLbl = new Label();
                    iconLbl.setStyle("-fx-font-size: 24px;");
                    Label titleLbl = new Label();
                    titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                    Label subLbl = new Label();
                    subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

                    if ("ENDED".equals(status)) {
                        iconLbl.setText("📹");
                        titleLbl.setText("Cuộc gọi video");
                        long min = duration / 60;
                        long sec = duration % 60;
                        subLbl.setText(String.format("Thời lượng: %02d:%02d", min, sec));
                    } else if ("MISSED".equals(status)) {
                        iconLbl.setText("📞");
                        titleLbl.setText("Cuộc gọi nhỡ");
                        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #e74c3c;");
                        subLbl.setText("Nhấn để gọi lại");
                    } else if ("REJECTED".equals(status)) {
                        iconLbl.setText("🚫");
                        titleLbl.setText("Cuộc gọi bị từ chối");
                        subLbl.setText(msg.getSender().equals(myName) ? "Đối phương bận" : "Bạn đã từ chối");
                    }
                    HBox contentBox = new HBox(10, iconLbl, new VBox(2, titleLbl, subLbl));
                    contentBox.setAlignment(Pos.CENTER_LEFT);
                    callBox.getChildren().add(contentBox);
                    bubble.getChildren().add(callBox);
                    break;
                case VOICE:
                    // --- GIAO DIỆN TIN NHẮN THOẠI ---
                    HBox voiceBox = new HBox(10);
                    voiceBox.setPadding(new Insets(5));
                    voiceBox.setAlignment(Pos.CENTER_LEFT);
                    voiceBox.setStyle("-fx-background-color: rgba(0,0,0,0.05); -fx-background-radius: 20; -fx-padding: 5 15 5 10;");

                    Button btnPlay = new Button("▶");
                    btnPlay.setStyle("-fx-background-radius: 50%; -fx-min-width: 30px; -fx-min-height: 30px; -fx-background-color: #0084ff; -fx-text-fill: white;");

                    Label lblDuration = new Label("Voice Message");
                    lblDuration.setStyle("-fx-font-size: 11px; -fx-text-fill: #555;");

                    String vFileName = msg.getFileName(); // Tên file voice

                    // Logic Play:
                    btnPlay.setOnAction(ev -> {
                        // 1. Kiểm tra file đã có chưa
                        File vDir = new File("client_downloads");
                        File vFile = new File(vDir, vFileName);

                        if (vFile.exists()) {
                            // Có rồi -> Play luôn
                            btnPlay.setText("🔊"); // Đổi icon đang phát
                            AudioRecorder.playAudio(vFile);
                            // Reset icon sau 3s (Demo đơn giản)
                            new Thread(() -> {
                                try {
                                    Thread.sleep(3000);
                                } catch (Exception ex) {
                                }
                                Platform.runLater(() -> btnPlay.setText("▶"));
                            }).start();
                        } else {
                            // Chưa có -> Tải về rồi Play
                            btnPlay.setText("⏳"); // Icon loading
                            parent.downloadFile(vFileName, null);

                            // Thread đợi tải xong
                            new Thread(() -> {
                                try {
                                    for (int i = 0; i < 20; i++) { // Đợi tối đa 10s
                                        Thread.sleep(500);
                                        if (vFile.exists()) {
                                            Platform.runLater(() -> {
                                                btnPlay.setText("🔊");
                                                AudioRecorder.playAudio(vFile);
                                            });
                                            // Reset icon
                                            try {
                                                Thread.sleep(3000);
                                            } catch (Exception ex) {
                                            }
                                            Platform.runLater(() -> btnPlay.setText("▶"));
                                            break;
                                        }
                                    }
                                } catch (Exception ex) {
                                }
                            }).start();
                        }
                    });

                    voiceBox.getChildren().addAll(btnPlay, lblDuration);
                    bubble.getChildren().add(voiceBox);
                    break;
            }

            timestamp.setText(MessageUtils.formatMessageTime(msg.getTimestamp()));
            timestamp.setAlignment(Pos.BOTTOM_RIGHT);
            timestamp.setMaxWidth(Double.MAX_VALUE);
            bubble.getChildren().add(timestamp);

            if (isMe) {
                bubbleContainer.getChildren().add(bubble);
            } else {
                VBox avatarContainer = new VBox(miniAvatar);
                avatarContainer.setAlignment(Pos.BOTTOM_CENTER);
                bubbleContainer.getChildren().addAll(avatarContainer, bubble);
            }

            rootBox.getChildren().add(bubbleContainer);
            setGraphic(rootBox);
        }
    }
}