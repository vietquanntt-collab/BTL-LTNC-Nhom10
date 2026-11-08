package com.btl.team10.client.service;

public class NetworkClient {
    private static NetworkClient instance;

    private NetworkClient() {}

    public static NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    // Hàm gọi mạng (hiện tại giả lập delay để thấy Loading state)
    public boolean login(String username, String password) throws Exception {
        // TODO: Tuần tới sẽ thay bằng code ghi/đọc TCP Socket hoặc HTTP thực tế ở đây
        Thread.sleep(1500); // Tạm dừng 1.5s để mô phỏng mạng chậm

        // Trả về true nếu tài khoản là admin/123456
        return "admin".equals(username) && "123456".equals(password);
    }
}