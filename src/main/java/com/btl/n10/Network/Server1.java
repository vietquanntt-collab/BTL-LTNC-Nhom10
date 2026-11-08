package com.btl.n10.Network;

import com.btl.n10.Connection.AuctionDAOImpl;
import com.btl.n10.Connection.DataConnection;
import com.btl.n10.Model.Entity.Auction;
import com.btl.n10.Model.Enums.AuctionStatus;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server1 {
    // Port mặc định lắng nghe kết nối socket
    private static final int PORT = 9090;

    // Bảng băm đồng bộ lưu danh sách client kèm token nhận diện
    public static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // Thread pool cố định phục vụ xử lý song song các kết nối client
    private final ExecutorService pool = Executors.newFixedThreadPool(10);
    private ServerSocket server;

    public Server1() throws IOException {
        server = new ServerSocket(PORT);
        System.out.println("Server dang chay tren port " + PORT);

        // Đăng ký hook dọn dẹp tiến trình và giải phóng tài nguyên khi tắt server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (server != null && !server.isClosed()) {
                    server.close();
                }
                pool.shutdown();
            } catch (IOException e) {
                System.err.println("Loi khi shutdown server: " + e.getMessage());
            }
        }));
        // Scheduler: cứ mỗi 10 giây kiểm tra auction hết giờ và settle
        startAuctionSettlementScheduler();

        while (true) {
            Socket clientSocket = server.accept();
            System.out.println("Client moi: " + clientSocket.getInetAddress());
            try {
                String token = UUID.randomUUID().toString();
                ClientHandler handler = new ClientHandler(clientSocket, clients, token);
                clients.put(token, handler);
                pool.execute(handler);
            } catch (IOException e) {
                System.err.println("Loi tao ClientHandler: " + e.getMessage());
                clientSocket.close();
            }
        }
    }
    /**
     * Mỗi 10 giây quét các auction OPEN đã quá end_time -> settle.
     * Đảm bảo winner luôn được xác định và balance bị trừ đúng giờ,
     * dù không có bid nào đến sau khi auction hết giờ.
     */
    private void startAuctionSettlementScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "auction-settler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            Connection conn = null;
            try {
                conn = DataConnection.getConnection();
                if (conn == null) return;

                AuctionDAOImpl auctionDAO = new AuctionDAOImpl(conn);
                List<Auction> allAuctions = auctionDAO.findAll();
                LocalDateTime now = LocalDateTime.now();

                for (Auction auction : allAuctions) {
                    if (auction.getStatus() == AuctionStatus.OPEN
                            && now.isAfter(auction.getEndTime())) {

                        System.out.println("[Scheduler] Settling expired auction id=" + auction.getId());

                        // Dùng một RequestHandler tạm (không gắn với client cụ thể)
                        // để tái dùng logic settleAuction
                        SettlementHandler settler = new SettlementHandler(clients);
                        settler.settleAuction(auction.getId());
                    }
                }
            } catch (Exception e) {
                System.err.println("[Scheduler] Loi khi settle: " + e.getMessage());
            } finally {
                // Đóng connection sau mỗi chu kỳ quét để giải phóng kết nối JDBC
                DataConnection.closeQuietly(conn);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }
    public static void main(String[] args) throws IOException {
        new Server1();
    }
}