package com.btl.team10.client.service;

import com.btl.team10.client.testing.ServerFixture;
import com.btl.team10.common.protocol.*;
import com.btl.team10.common.serialization.JsonCodec;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class NetworkClientTest {
    @Test void sellerCanCreateReplacePreserveAndRemoveImageThroughActualRoutes() throws Exception {
        try (var fixture = new ServerFixture(); var client = new NetworkClient("127.0.0.1", fixture.port(), 2500)) {
            client.login("seller", "seller123");
            long id = client.createItem("Image test", "FIGURE", "Fixture", "YWJj");
            assertEquals("YWJj", client.listItems().stream().filter(i->i.itemId()==id).findFirst().orElseThrow().imageBase64());
            client.updateItem(id,"Image test", "FIGURE", "Changed", "ZGVm");
            assertEquals("ZGVm", client.listItems().stream().filter(i->i.itemId()==id).findFirst().orElseThrow().imageBase64());
            client.updateItem(id,"Keep image", "FIGURE", "Preserved");
            assertEquals("ZGVm", client.listItems().stream().filter(i->i.itemId()==id).findFirst().orElseThrow().imageBase64());
            client.updateItem(id,"Clear image", "FIGURE", "Cleared", "");
            assertNull(client.listItems().stream().filter(i->i.itemId()==id).findFirst().orElseThrow().imageBase64());
            assertThrows(IOException.class, () -> client.createItem("Bad", "FIGURE", "", "%%%"));
            assertThrows(IOException.class, () -> client.createItem("Large", "FIGURE", "", "A".repeat(699056)));
        }
    }
    @Test void logoutThenLoginCannotBeClosedByPreviousReader() throws Exception {
        try (var fixture = new ServerFixture(); var client = new NetworkClient("127.0.0.1", fixture.port(), 2500)) {
            for (int i=0;i<4;i++) {
                client.login("bidder1", "bidder123"); assertFalse(client.listAuctions("ALL").isEmpty()); client.logout();
            }
            client.login("bidder2", "bidder123"); assertEquals("bidder2",client.account());
            assertFalse(client.getAuctionDetail(1).bids().isEmpty());
        }
    }
    @Test void eofFailsPendingRequestPromptlyInsteadOfWaitingForTimeout() throws Exception {
        try (ServerSocket server = new ServerSocket(0); var client = new NetworkClient("127.0.0.1",server.getLocalPort(),10000)) {
            CompletableFuture<Void> peer = CompletableFuture.runAsync(() -> {
                try (Socket socket=server.accept()) { socket.getInputStream().read(new byte[4096]); }
                catch (IOException error) { throw new UncheckedIOException(error); }
            });
            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(IOException.class, ()->client.login("x","y")));
            peer.get(3,TimeUnit.SECONDS);
        }
    }
    @Test void reconnectRestoresWalletBindingAndCachedBalanceWithoutNewLogin() throws Exception {
        try (ServerSocket proxy=new ServerSocket(0);
             var client=new NetworkClient("127.0.0.1",proxy.getLocalPort(),2500)) {
            CountDownLatch resumed=new CountDownLatch(1), wallet=new CountDownLatch(1);
            AtomicReference<Throwable> error=new AtomicReference<>();
            var executor=Executors.newSingleThreadExecutor();
            try {
                Future<?> peer=executor.submit(()-> {
                    try {
                        try(Socket first=proxy.accept()) { replyLogin(first); }
                        try(Socket second=proxy.accept()) {
                            var reader=new BufferedReader(new InputStreamReader(second.getInputStream(),StandardCharsets.UTF_8));
                            var writer=new PrintWriter(second.getOutputStream(),true,StandardCharsets.UTF_8);
                            var request=JsonCodec.fromJson(reader.readLine(),RequestEnvelope.class);
                            assertEquals("SESSION",request.action());
                            writer.println(JsonCodec.toJson(ResponseEnvelope.success(request.requestId(),request.action(),"session-test",JsonCodec.toJson(java.util.Map.of("balanceCents",900,"frozenCents",100)))));
                            resumed.await(3,TimeUnit.SECONDS);
                        }
                    } catch(Throwable problem) { error.set(problem); }
                });
                client.addConnectionListener(connected->{if(connected) resumed.countDown();});
                client.setWalletListener((balance,frozen)->{if(balance==900 && frozen==100) wallet.countDown();});
                client.login("bidder1","bidder123");
                assertTrue(resumed.await(5,TimeUnit.SECONDS)); assertTrue(wallet.await(1,TimeUnit.SECONDS));
                assertEquals(900,client.balanceCents()); assertTrue(client.isLoggedIn());
                client.close(); peer.get(5,TimeUnit.SECONDS); assertNull(error.get());
            } finally { executor.shutdownNow(); }
        }
    }
    private static void replyLogin(Socket socket) throws Exception {
        var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
        var writer=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8);
        var request=JsonCodec.fromJson(reader.readLine(),RequestEnvelope.class);
        writer.println(JsonCodec.toJson(ResponseEnvelope.success(request.requestId(),request.action(),"session-test",
                JsonCodec.toJson(java.util.Map.of("userId",3,"account","bidder1","role","BIDDER","balanceCents",1000,"frozenCents",0)))));
        Thread.sleep(150);
    }
}
