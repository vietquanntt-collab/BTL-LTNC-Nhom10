package com.btl.n10.Connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Lớp cấu hình và quản lý kết nối cơ sở dữ liệu MySQL cho Server.
 * Dùng ThreadLocal để mỗi luồng Client khi kết nối vào sẽ giữ một kết nối riêng,
 * tránh việc các luồng xung đột dữ liệu khi thực hiện truy vấn đồng thời.
 */
public abstract class DataConnection {

    // Thông tin cấu hình kết nối tới database MySQL
    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "mainline.proxy.rlwy.net");
    private static final String DB_PORT = System.getenv().getOrDefault("DB_PORT", "42198");
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "railway");

    // Chuỗi URL kết nối JDBC kèm cấu hình mã hóa và múi giờ
    private static final String URL = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            DB_HOST, DB_PORT, DB_NAME
    );
    private static final String USER = System.getenv().getOrDefault("DB_USER", "member2");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    // Biến lưu trữ kết nối riêng biệt cho từng luồng thực thi
    private static final ThreadLocal<Connection> threadConn = new ThreadLocal<>();

    // Constructor để protected để không tạo đối tượng trực tiếp bên ngoài
    protected DataConnection() {}

    /**
     * Hàm lấy đối tượng Connection cho luồng hiện tại.
     * Kiểm tra kết nối cũ, nếu chưa có hoặc bị đứt thì tự khởi tạo kết nối mới.
     */
    public static Connection getConnection() {
        try {
            // Lấy kết nối được lưu trong luồng hiện tại ra kiểm tra
            Connection conn = threadConn.get();

            // Kiểm tra kết nối null, đã đóng hoặc ping thử tới server DB trong 2s không phản hồi
            if (conn == null || conn.isClosed() || !conn.isValid(2)) {
                // Khởi tạo kết nối mới thông qua DriverManager
                conn = DriverManager.getConnection(URL, USER, PASSWORD);
                // Lưu kết nối mới vào lại luồng
                threadConn.set(conn);
            }
            return conn;
        } catch (SQLException e) {
            // In thông báo nếu kết nối tới database thất bại
            System.err.println("[DataConnection] Lỗi khởi tạo kết nối CSDL: " + e.getMessage());
            return null;
        }
    }

    /**
     * Hàm đóng kết nối của luồng hiện tại khi Client ngắt kết nối khỏi hệ thống.
     */
    public static void closeConnection() {
        try {
            Connection conn = threadConn.get();
            // Nếu kết nối đang mở thì tiến hành đóng lại
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("[DataConnection] Lỗi khi đóng kết nối: " + e.getMessage());
        } finally {
            // Xóa bỏ biến lưu trữ trong ThreadLocal để giải phóng bộ nhớ cho luồng
            threadConn.remove();
        }
    }

    /**
     * Hàm tiện ích hỗ trợ đóng nhanh các tài nguyên JDBC như ResultSet, Statement
     * Giúp code ở các lớp DAO gọn hơn và tránh rò rỉ bộ nhớ.
     */
    public static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (Exception ignored) {
                    // Bỏ qua lỗi đóng tài nguyên phụ
                }
            }
        }
    }
}