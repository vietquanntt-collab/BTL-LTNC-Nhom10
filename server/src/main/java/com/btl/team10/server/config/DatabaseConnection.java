package com.btl.team10.server.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private static final String DB_URL = "jdbc:sqlite:database/auction.db";
    private static final ThreadLocal<Connection> threadLocalConnection = new ThreadLocal<>();

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = threadLocalConnection.get();
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
            threadLocalConnection.set(conn);
        }
        return conn;
    }

    public static void closeCurrentConnection() {
        Connection conn = threadLocalConnection.get();
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                }
            } catch (SQLException ignored) {
            } finally {
                threadLocalConnection.remove();
            }
        }
    }
}