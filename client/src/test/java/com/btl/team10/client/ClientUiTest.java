package com.btl.team10.client;

import com.btl.team10.client.controller.*;
import com.btl.team10.client.navigation.NavigationManager;
import com.btl.team10.client.service.NetworkClient;
import com.btl.team10.client.testing.ServerFixture;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import com.btl.team10.client.util.StageAssetLoader;
import com.btl.team10.common.dto.AuctionDto;
import com.btl.team10.common.dto.BidDto;
import com.btl.team10.common.model.AuctionStatus;
import com.btl.team10.common.model.BidStatus;
import java.time.Instant;
import javafx.scene.layout.*;
import org.junit.jupiter.api.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Loads actual FXML, fires actual handlers and renders application-owned scenes. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "runUiTests", matches = "true")
class ClientUiTest {
    private ServerFixture fixture;
    private final AtomicReference<Throwable> fxError = new AtomicReference<>();
    private Parent shell;
    private MainShellController controller;
    private Scene scene;
    @BeforeAll void start() throws Exception {
        fixture = new ServerFixture();
        System.setProperty("auction.server.port", String.valueOf(fixture.port()));
        var started = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false);
            Thread.currentThread().setUncaughtExceptionHandler((t,e)->fxError.compareAndSet(null,e)); started.countDown(); });
        assertTrue(started.await(15,TimeUnit.SECONDS));
    }
    @AfterAll void stop() throws Exception {
        NetworkClient.getInstance().close();
        fx(() -> { NavigationManager.getInstance().switchState(com.btl.team10.client.state.UIState.EMPTY); return null; });
        fixture.close(); Platform.exit();
    }
    @Test void allScreensRenderAndMainUserFlowsWorkWithRealServer() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/btl/team10/client/view/MainShell.fxml"));
            shell=loader.load(); controller=loader.getController(); scene=new Scene(shell,1280,800);
            return null;
        });
        snapshot("login",1280,800);
        fx(() -> { find("registerTabBtn",Button.class).fire(); return null; });
        snapshot("register",1100,720);
        fx(() -> { find("usernameField",TextField.class).setText("new-user");
            find("passwordField",PasswordField.class).setText("abcdef");
            find("confirmPasswordField",PasswordField.class).setText("different");
            find("submitButton",Button.class).fire();
            assertTrue(find("feedbackLabel",Label.class).isVisible());
            find("loginTabBtn",Button.class).fire();
            find("usernameField",TextField.class).setText("bidder1"); find("passwordField",PasswordField.class).setText("bidder123");
            find("submitButton",Button.class).fire(); return null; });
        await(() -> fxUnchecked(() -> shell.lookup("#kpiTotalLabel") != null && find("kpiTotalLabel",Label.class).getText().startsWith("1")));
        snapshot("auctions",1280,800); snapshot("auctions-small",1100,720);
        fx(() -> { find("searchField",TextField.class).setText("no-such-item"); return null; });
        assertEquals("0 phiên",fx(()->find("kpiTotalLabel",Label.class).getText()));
        fx(() -> { find("searchField",TextField.class).clear(); return null; });
        assertEquals("1 phiên",fx(()->find("kpiTotalLabel",Label.class).getText()));
        var initial=NetworkClient.getInstance().getAuctionDetail(1).auction();
        fx(() -> { var detail=NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionDetail.fxml",AuctionDetailController.class);
            assertNotNull(detail); detail.initAuction(initial); return null; });
        await(()->fxUnchecked(()->find("itemNameLabel",Label.class).getText().contains("Demo trading")));
        snapshot("detail",1280,800); snapshot("detail-small",1100,720);
        assertStageAndHistoryReadable();
        assertFalse(fx(()->find("placeBidButton",Button.class).isDisabled()));
        fx(()->{find("bidAmountField",TextField.class).setText("115000");find("placeBidButton",Button.class).fire();return null;});
        await(()->fxUnchecked(()->find("leadStatusLabel",Label.class).getText().contains("ĐANG DẪN")));
        assertEquals(115000,NetworkClient.getInstance().getAuctionDetail(1).auction().currentPriceCents());
        fx(()->{find("autoBidMaxField",TextField.class).setText("150000");find("autoBidIncrementField",TextField.class).setText("5000");find("setAutoBidButton",Button.class).fire();return null;});
        await(()->fxUnchecked(()->find("autoBidStatusLabel",Label.class).getText().contains("Đang bật")));
        fx(()->{find("cancelAutoBidButton",Button.class).fire();return null;});
        await(()->fxUnchecked(()->find("autoBidStatusLabel",Label.class).getText().contains("Đã tắt")));
        switchRole("seller","seller123","SellerDashboard.fxml");
        await(()->fxUnchecked(()->!find("itemTable",TableView.class).getItems().isEmpty()));
        snapshot("seller",1280,800); snapshot("seller-small",1100,720);
        fx(()-> { ((TableView<?>)shell.lookup("#itemTable")).getSelectionModel().clearSelection(); return null; });
        // Actual preset handler must send a supported ItemType and preserve the image.
        fx(()-> { Button preset=(Button)shell.lookupAll(".button").stream().filter(n->n instanceof Button b && b.getText().equals("Cúp Euro")).findFirst().orElseThrow();
            preset.fire();find("createItemBtn",Button.class).fire();return null; });
        await(()->fxUnchecked(()->find("itemTable",TableView.class).getItems().size()==2));
        assertTrue(NetworkClient.getInstance().listItems().stream().anyMatch(i->i.name().contains("Euro") && i.imageBase64()!=null));
        switchRole("admin","admin123","AdminDashboard.fxml");
        await(()->fxUnchecked(()->find("userTable",TableView.class).getItems().size()==4));
        snapshot("admin",1280,800); snapshot("admin-small",1100,720);
        NetworkClient.getInstance().cancelAuction(1);
        var cancelled=NetworkClient.getInstance().getAuctionDetail(1).auction();
        fx(()-> { var detail=NavigationManager.getInstance().loadView("/com/btl/team10/client/view/AuctionDetail.fxml",AuctionDetailController.class);detail.initAuction(cancelled);return null; });
        await(()->fxUnchecked(()->find("winnerBannerBox",HBox.class).isVisible()));
        snapshot("closed",1280,800);
        assertTrue(fx(()->find("placeBidButton",Button.class).isDisabled()));
        fx(()-> { for(var state:com.btl.team10.client.state.UIState.values()) {
            var node=(Parent)new FXMLLoader(getClass().getResource(state.getFxmlPath())).load();
            new Scene(node,800,600);node.applyCss();node.layout(); assertFalse(node.getChildrenUnmodifiable().isEmpty());
        } return null; });
        assertNull(fxError.get(),()->"Uncaught JavaFX exception: "+fxError.get());
    }
    @Test void borderCutoutsKeepInteriorDetailsOnBothBlackAndWhiteBackgrounds() throws Exception {
        fx(() -> {
            for (Color border : new Color[]{Color.BLACK, Color.WHITE}) {
                WritableImage input = new WritableImage(9, 9);
                for (int y = 0; y < 9; y++) for (int x = 0; x < 9; x++) {
                    boolean object = x >= 2 && x <= 6 && y >= 2 && y <= 6;
                    input.getPixelWriter().setColor(x, y, object ? Color.DODGERBLUE : border);
                }
                input.getPixelWriter().setColor(4, 4, border);
                var cutout = StageAssetLoader.stripOuterBlackAndCrop(input);
                assertEquals(5, cutout.getWidth());
                assertEquals(5, cutout.getHeight());
                assertEquals(border, cutout.getPixelReader().getColor(2, 2),
                        "Interior detail must survive border flood fill");
                assertEquals(Color.DODGERBLUE, cutout.getPixelReader().getColor(0, 0));
            }
            return null;
        });
    }

    @Test void closedAuctionsOpenWithoutCountdownErrorsAndKeepControlsLocked() throws Exception {
        fx(() -> {
            Instant now = Instant.now();
            for (AuctionStatus status : new AuctionStatus[]{AuctionStatus.CANCELLED,
                    AuctionStatus.FINISHED, AuctionStatus.PAID}) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(
                        "/com/btl/team10/client/view/AuctionDetail.fxml"));
                Parent root = loader.load();
                AuctionDetailController detail = loader.getController();
                new Scene(root, 1100, 720);
                var auction = new AuctionDto(1, 1, 100000, 110000, 5000,
                        now.minusSeconds(3600), now.minusSeconds(1), status, 3, 0,
                        status == AuctionStatus.CANCELLED ? null : 4L, now, now.minusSeconds(4000));
                assertDoesNotThrow(() -> detail.initAuction(auction));
                root.applyCss(); root.layout();
                assertTrue(((Button)root.lookup("#placeBidButton")).isDisabled());
                assertTrue(((Button)root.lookup("#setAutoBidButton")).isDisabled());
                assertTrue(((Button)root.lookup("#cancelAutoBidButton")).isDisabled());
                assertTrue(((HBox)root.lookup("#winnerBannerBox")).isVisible());
                detail.cleanup();
            }
            return null;
        });
    }

    @Test void registrationAndValidationFeedbackFitInsideTheLoginWindow() throws Exception {
        fx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/com/btl/team10/client/view/login.fxml"));
            Parent root = loader.load();
            new Scene(root, 1100, 720);
            ((Button)root.lookup("#registerTabBtn")).fire();
            ((Button)root.lookup("#submitButton")).fire();
            root.resize(1100, 720); root.applyCss(); root.layout();
            var card = root.lookup("#authCardBox");
            var bounds = card.localToScene(card.getLayoutBounds());
            assertTrue(bounds.getMinY() >= 0 && bounds.getMaxY() <= 721,
                    "Registration form and error feedback must remain reachable");
            ((LoginController)loader.getController()).cleanup();
            return null;
        });
    }

    private void assertStageAndHistoryReadable() throws Exception {
        fx(() -> {
            var image = find("mcImageView", ImageView.class).getImage();
            assertNotNull(image, "MC pose must be packaged with the stage");
            assertFalse(image.isError());
            VBox bubble = find("mcSpeechBubble", VBox.class);
            Color background = (Color) bubble.getBackground().getFills().getFirst().getFill();
            Color foreground = (Color) find("mcDialogueLabel", Label.class).getTextFill();
            assertTrue(contrast(foreground, background) >= 4.5,
                    "MC dialogue needs readable contrast after controller updates");
            TableView<?> table = find("bidHistoryTable", TableView.class);
            table.applyCss(); table.layout();
            boolean checked = false;
            for (Node node : table.lookupAll(".table-row-cell")) {
                if (node instanceof TableRow<?> row && row.getItem() instanceof BidDto bid
                        && bid.status() == BidStatus.ACTIVE) {
                    Color rowBackground = (Color) row.getBackground().getFills().getFirst().getFill();
                    for (Node cell : row.lookupAll(".table-cell")) {
                        if (cell instanceof TableCell<?,?> value && value.getText() != null) {
                            assertTrue(contrast((Color)value.getTextFill(), rowBackground) >= 4.5,
                                    "Leading-bid cells need readable contrast");
                            checked = true;
                        }
                    }
                }
            }
            assertTrue(checked, "Regression must inspect the rendered leading bid");
            return null;
        });
    }

    private static double contrast(Color foreground, Color background) {
        double first = luminance(foreground), second = luminance(background);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(Color color) {
        return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen())
                + .0722 * linear(color.getBlue());
    }

    private static double linear(double channel) {
        return channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4);
    }

    private void switchRole(String account,String password,String view) throws Exception {
        fx(()->{NavigationManager.getInstance().switchState(com.btl.team10.client.state.UIState.LOADING);return null;});
        NetworkClient.getInstance().logout(); NetworkClient.getInstance().login(account,password);
        fx(()->{controller.refreshSessionState();NavigationManager.getInstance().loadView("/com/btl/team10/client/view/"+view);return null;});
    }
    private <T> T find(String id,Class<T> type) {
        shell.applyCss(); shell.layout();
        return type.cast(java.util.Objects.requireNonNull(shell.lookup("#"+id),id));
    }
    private void snapshot(String name,int width,int height) throws Exception {
        Thread.sleep(300);
        fx(()-> { shell.resize(width,height);shell.applyCss();shell.layout();
            for (Node node : shell.lookupAll(".scroll-pane")) {
                if (node instanceof ScrollPane scroll && scroll.isFitToWidth() && scroll.getContent() != null) {
                    double viewportWidth = scroll.getViewportBounds().getWidth();
                    double contentWidth = scroll.getContent().getLayoutBounds().getWidth();
                    Files.writeString(Path.of("target/ui-layout.tsv"), name+"\t"+viewportWidth+"\t"+contentWidth+"\n",
                            StandardOpenOption.CREATE,StandardOpenOption.APPEND);
                    assertTrue(contentWidth <= viewportWidth + 2, () -> name+": content overflows viewport ("+contentWidth+" > "+viewportWidth+")");
                }
            }
            WritableImage image=shell.snapshot(null,new WritableImage(width,height));
            BufferedImage output=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<height;y++)for(int x=0;x<width;x++)output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
            Path file=Path.of("target/ui-snapshots",name+".png");Files.createDirectories(file.getParent());ImageIO.write(output,"png",file.toFile()); return null;
        });
    }
    private static <T> T fx(Callable<T> action) throws Exception { FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS); }
    private static boolean fxUnchecked(Callable<Boolean> action) { try{return fx(action);}catch(Exception error){throw new RuntimeException(error);} }
    private static void await(BooleanSupplier ready) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline){if(ready.getAsBoolean())return;Thread.sleep(40);}fail("UI condition did not become ready within 10 seconds");
    }
}
