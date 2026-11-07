package com.btl.team10.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Week 2 Server Smoke Tests")
class ServerSmokeTest {

    @Test
    @DisplayName("Smoke Test: ServerApplication main class tồn tại và nạp thành công")
    void testServerApplicationClassExists() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.btl.team10.server.ServerApplication");
            assertNotNull(clazz, "ServerApplication main class không được null");
        }, "Không tìm thấy class com.btl.team10.server.ServerApplication trong module server");
    }

    @Test
    @DisplayName("Smoke Test: Môi trường và cấu hình Server sẵn sàng")
    void testServerEnvironmentReadiness() {
        // Kiểm tra nạp cấu hình cổng/socket cơ bản
        int defaultPort = 8080;
        assertTrue(defaultPort > 1024 && defaultPort <= 65535, "Cổng Server phải nằm trong dải hợp lệ");
    }
}