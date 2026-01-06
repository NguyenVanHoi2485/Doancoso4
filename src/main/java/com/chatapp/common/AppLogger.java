package com.chatapp.common;

import java.util.logging.*;

public class AppLogger {
    private static final Logger LOGGER = Logger.getLogger("ChatApp");

    static {
        try {
            // Remove default handlers
            LOGGER.setUseParentHandlers(false);

            // Create console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter() {
                private static final String FORMAT = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

                @Override
                public synchronized String format(LogRecord record) {
                    return String.format(FORMAT,
                            new java.util.Date(record.getMillis()),
                            record.getLevel().getLocalizedName(),
                            record.getMessage()
                    );
                }
            });
            LOGGER.addHandler(consoleHandler);

            // Set level
            LOGGER.setLevel(Level.ALL);
            consoleHandler.setLevel(Level.ALL);

        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warning(String message) {
        LOGGER.warning(message);
    }

    public static void severe(String message) {
        LOGGER.severe(message);
    }

    public static void severe(String message, Throwable throwable) {
        LOGGER.log(Level.SEVERE, message, throwable);
    }
}