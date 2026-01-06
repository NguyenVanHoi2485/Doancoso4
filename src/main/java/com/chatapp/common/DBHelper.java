package com.chatapp.common;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DBHelper {

    private static final Logger logger = Logger.getLogger(DBHelper.class.getName());
    private static HikariDataSource dataSource;

    static {
        try (var in = DBHelper.class.getResourceAsStream("/db.properties")) {
            if (in == null) throw new RuntimeException("db.properties not found on classpath!");
            Properties p = new Properties();
            p.load(in);

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(p.getProperty("db.url"));
            config.setUsername(p.getProperty("db.user"));
            config.setPassword(p.getProperty("db.pass"));

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(30000);
            config.setConnectionTimeout(30000);
            config.setPoolName("ChatAppPool");

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            dataSource = new HikariDataSource(config);
            AppLogger.info("✅ Database Connection Pool started successfully.");

        } catch (IOException e) {
            throw new RuntimeException("Failed to load db.properties", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize Connection Pool", e);
        }
    }

    public static Connection get() throws SQLException {
        return dataSource.getConnection();
    }

    public static int executeUpdate(String sql, Object... params) {
        try (Connection conn = get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error executing update: " + sql, e);
            return 0;
        }
    }

    public static long executeInsertAndGetId(String sql, Object... params) {
        long generatedId = -1;
        try (Connection conn = get();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) generatedId = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error inserting and getting ID: " + sql, e);
        }
        return generatedId;
    }

    public interface ResultSetProcessor {
        void process(ResultSet rs) throws SQLException;
    }

    public static void executeQuery(String sql, ResultSetProcessor processor, Object... params) {
        try (Connection conn = get();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                processor.process(rs);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error executing query: " + sql, e);
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            AppLogger.info("Database Connection Pool closed.");
        }
    }

    public static void updateUserOnlineStatus(String username, boolean online) {
        executeUpdate("INSERT INTO users (username, online, last_seen) VALUES (?, ?, NOW()) ON DUPLICATE KEY UPDATE online = ?, last_seen = NOW()", username, online, online);
    }

    public static void createUser(String username, String password) {
        String hashedPassword = PasswordHasher.hashPassword(password);
        executeUpdate(
                "INSERT INTO users (username, password, online, last_seen) VALUES (?, ?, FALSE, NOW())",
                username, hashedPassword
        );
    }
}



