package com.chatapp.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;

import java.util.Optional;
import java.util.function.Consumer;

public class EmojiPicker extends Dialog<String> {

    // Danh sách Emoji (Đã chọn lọc các icon phổ biến và hỗ trợ tốt)
    private static final String[] EMOJI_CATEGORIES = {
            "😀😃😄😁😆😅😂🤣😊😇🙂🙃😉😌😍🥰😘😗😙😚",
            "😋😛😝😜🤪🤨🧐🤓😎🥸🤩🥳😏😒😞😔😟😕🙁☹️",
            "😣😖😫😩🥺😢😭😤😠😡🤬🤯😳🥵🥶😱😨😰😥😓",
            "🤗🤔🤭🤫🤥😶😐😑😬🙄😯😦😧😮😲🥱😴🤤😪😵",
            "🥴🤢🤮🤧😷🤒🤕🤑🤠😈👿👹👺🤡💩👻💀☠️👽",
            "👋🤚🖐️✋🖖👌🤌🤏✌️🤞🤟🤘👈👉👆🖕👇☝️👍👎",
            "✊👊🤛🤜👏🙌👐🤲🤝🙏✍️💅🤳💪🦵🦶👂🦻👃",
            "❤️🧡💛💚💙💜🖤🤍🤎💔❣️💕💞💓💗💖💘💝💟",
            "✅✔️❌❎✨🌟💫⭐🔥💥💦💧💨💤💭🗯️💬🗨️",
            "🎉🎊🎈🎂🎀🎁📯🎷🎸🎹🎺🎻🪕🥁📱💻🖥️🖨️🖱️",
            "🚗🚕🚙🚌🚎🏎️🚓🚑🚒🚐🛻🚚🚛🚜🏍️🛵🦽🦼🚲",
            "🐶🐱🐭🐹🐰🦊🐻🐼🐻‍❄️🐨🐯🦁🐮🐷🐽🐸🐵🙈🙉",
            "🍏🍎🍐🍊🍋🍌🍉🍇🍓🫐🍈🍒🍑🥭🍍🥥🥝🍅🍆",
            "⚽🏀🏈⚾🥎🎾🏐🏉🎱🥏🏓🏸🏒🥅🥋🥊🎣🤿🎽"
    };

    public EmojiPicker() {
        setTitle("Choose an Emoji");
        setHeaderText(null); // Bỏ header text để giao diện gọn hơn

        // SỬA LỖI 1: Xử lý ClassCastException
        // Khi đóng dialog bằng nút X hoặc Cancel, trả về null thay vì ButtonType
        setResultConverter(new Callback<ButtonType, String>() {
            @Override
            public String call(ButtonType param) {
                return null;
            }
        });

        GridPane emojiGrid = new GridPane();
        emojiGrid.setHgap(5);
        emojiGrid.setVgap(5);
        emojiGrid.setPadding(new Insets(10));
        emojiGrid.setStyle("-fx-background-color: white;"); // Nền trắng sạch

        int row = 0;
        int col = 0;
        final int COLUMNS = 8; // Giảm số cột để icon to hơn

        for (String category : EMOJI_CATEGORIES) {
            int[] codePoints = category.codePoints().toArray();

            for (int codePoint : codePoints) {
                String emoji = new String(Character.toChars(codePoint));
                if (emoji.trim().isEmpty()) continue;

                Button emojiButton = new Button(emoji);

                String baseStyle =
                        "-fx-background-color: transparent; " +
                                "-fx-text-fill: black; " +
                                "-fx-font-size: 22px; " +   // GIẢM TỪ 28px XUỐNG 22px
                                "-fx-cursor: hand; " +
                                "-fx-background-radius: 5px; " +
                                "-fx-padding: 0; " +        // QUAN TRỌNG: Xóa padding thừa
                                "-fx-alignment: center; " + // Căn giữa
                                "-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;";

                emojiButton.setStyle(baseStyle);

                // Hiệu ứng Hover
                emojiButton.setOnMouseEntered(e ->
                        emojiButton.setStyle(baseStyle + "-fx-background-color: #f0f2f5;")
                );
                emojiButton.setOnMouseExited(e ->
                        emojiButton.setStyle(baseStyle)
                );

                emojiButton.setOnAction(e -> {
                    setResult(emoji); // Trả về emoji được chọn
                    close(); // Đóng dialog
                });

                emojiGrid.add(emojiButton, col, row);
                col++;
                if (col >= COLUMNS) {
                    col = 0;
                    row++;
                }
            }
            // Xuống dòng sau mỗi category
            if (col > 0) {
                col = 0;
                row++;
            }
        }

        ScrollPane scrollPane = new ScrollPane(emojiGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(420, 400);
        // Tắt viền scrollpane cho đẹp
        scrollPane.setStyle("-fx-background: white; -fx-border-color: transparent; -fx-background-color: white;");

        getDialogPane().setContent(scrollPane);
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

        // CSS nhỏ cho dialog pane
        getDialogPane().setStyle("-fx-font-family: 'Segoe UI Emoji', sans-serif;");
    }

    public static void showEmojiPicker(javafx.stage.Window owner, Consumer<String> onEmojiSelected) {
        EmojiPicker picker = new EmojiPicker();
        picker.initOwner(owner);

        // Sửa vị trí xuất hiện (Nằm giữa cửa sổ cha)
        picker.setOnShown(e -> {
            picker.setX(owner.getX() + owner.getWidth() / 2 - picker.getWidth() / 2);
            picker.setY(owner.getY() + owner.getHeight() / 2 - picker.getHeight() / 2);
        });

        Optional<String> result = picker.showAndWait();
        result.ifPresent(onEmojiSelected);
    }
}