package com.chatapp.ui;

import com.chatapp.model.Message;
import com.chatapp.common.MessageUtils;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class SearchPanel extends VBox {

    private final TextField searchField;
    private final ListView<Message> searchResults;
    private final ObservableList<Message> allMessages;
    private final FilteredList<Message> filteredMessages;
    private final Consumer<Message> onMessageSelected;

    public SearchPanel(ObservableList<Message> messages, Consumer<Message> onMessageSelected) {
        this.allMessages = messages;
        this.onMessageSelected = onMessageSelected;
        this.filteredMessages = new FilteredList<>(allMessages);

        setPadding(new Insets(10));
        setSpacing(10);
        setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1;");

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search messages...");
        searchField.textProperty().addListener((obs, old, newVal) -> performSearch(newVal));

        // Search results
        searchResults = new ListView<>(filteredMessages);
        searchResults.setCellFactory(lv -> new SearchResultCell());
        searchResults.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Message selected = searchResults.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onMessageSelected.accept(selected);
                }
            }
        });

        VBox.setVgrow(searchResults, Priority.ALWAYS);
        getChildren().addAll(createSearchHeader(), searchResults);
    }

    private HBox createSearchHeader() {
        Label title = new Label("Search Messages");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        Button closeButton = new Button("✕");
        closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #6c757d;");
        closeButton.setOnAction(e -> setVisible(false));

        HBox header = new HBox(10, title, searchField, closeButton);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        return header;
    }

    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            filteredMessages.setPredicate(null);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredMessages.setPredicate(message ->
                    message.getContent().toLowerCase().contains(lowerQuery) ||
                            message.getSender().toLowerCase().contains(lowerQuery)
            );
        }
    }

    private static class SearchResultCell extends ListCell<Message> {
        private final VBox container = new VBox(2);
        private final HBox header = new HBox(5);
        private final Label senderLabel = new Label();
        private final Label timeLabel = new Label();
        private final Label contentLabel = new Label();

        public SearchResultCell() {
            container.setPadding(new Insets(5));

            senderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
            timeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11px;");
            contentLabel.setStyle("-fx-font-size: 13px;");
            contentLabel.setWrapText(true);

            header.getChildren().addAll(senderLabel, timeLabel);
            container.getChildren().addAll(header, contentLabel);
        }

        @Override
        protected void updateItem(Message message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setGraphic(null);
                setText(null);
            } else {
                senderLabel.setText(message.getSender());
                timeLabel.setText(MessageUtils.formatMessageTime(message.getTimestamp()));
                contentLabel.setText(message.getContent());
                setGraphic(container);
            }
        }
    }
}