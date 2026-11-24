package com.btl.team10.client.util;

import com.btl.team10.common.dto.ItemDto;
import com.btl.team10.common.model.ItemType;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tải tài nguyên hình ảnh cho sân khấu 2D, hỗ trợ cả file bị lặp đuôi (.jpg.jpg),
 * tự động tách cả nền đen và nền trắng viền ngoài, đồng thời tự khớp hình minh họa vật phẩm.
 */
public final class StageAssetLoader {

    private static final String IMAGE_DIR = "/com/btl/team10/client/images/";
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private static final byte BG_NONE = 0;
    private static final byte BG_BLACK = 1;
    private static final byte BG_WHITE = 2;

    private StageAssetLoader() {
    }

    public static Image loadRawBackdrop(String baseName) {
        return CACHE.computeIfAbsent("raw:" + baseName, key -> {
            byte[] bytes = readResourceBytes(baseName);
            if (bytes == null) {
                return null;
            }
            Image img = new Image(new ByteArrayInputStream(bytes));
            return img.isError() ? null : img;
        });
    }

    public static Image loadCutoutSprite(String baseName) {
        return CACHE.computeIfAbsent("cutout:" + baseName, key -> {
            byte[] bytes = readResourceBytes(baseName);
            if (bytes == null) {
                return null;
            }
            Image raw = new Image(new ByteArrayInputStream(bytes));
            if (raw.isError()) {
                return null;
            }
            return stripOuterBlackAndCrop(raw);
        });
    }

    public static String loadPresetAsBase64(String baseName) {
        byte[] bytes = readResourceBytes(baseName);
        if (bytes == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static Image decodeAndCutoutBase64(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            Image raw = new Image(new ByteArrayInputStream(bytes));
            if (raw.isError()) {
                return null;
            }
            return stripOuterBlackAndCrop(raw);
        } catch (Exception e) {
            return null;
        }
    }

    public static Image resolveItemStageImage(ItemDto item) {
        if (item == null) {
            return loadCutoutSprite("euro_trophy");
        }
        Image uploaded = decodeAndCutoutBase64(item.imageBase64());
        if (uploaded != null) {
            return uploaded;
        }

        String text = ((item.name() == null ? "" : item.name()) + " "
                + (item.description() == null ? "" : item.description())).toLowerCase(Locale.ROOT);

        if (text.contains("bóng vàng") || text.contains("ballon") || text.contains("fifa") || text.contains("quả bóng")) {
            return loadCutoutSprite("ballon_dor");
        }
        if (text.contains("messi") || text.contains("goat") || text.contains("áo") || text.contains("shirt")) {
            return loadCutoutSprite("messi_goat");
        }
        if (text.contains("euro") || text.contains("cúp") || text.contains("cup") || text.contains("uefa")) {
            return loadCutoutSprite("euro_trophy");
        }

        ItemType type = item.itemType();
        if (type == ItemType.FIGURE) {
            return loadCutoutSprite("ballon_dor");
        }
        if (type == ItemType.POSTER) {
            return loadCutoutSprite("messi_goat");
        }
        return loadCutoutSprite("euro_trophy");
    }

    private static byte[] readResourceBytes(String baseName) {
        String[] extensions = {
                ".png", ".jpg", ".jpeg", ".JPG", ".PNG", ".JPEG",
                ".jpg.jpg", ".jpg.png", ".png.jpg", ".jpeg.png"
        };
        for (String ext : extensions) {
            try (InputStream is = StageAssetLoader.class.getResourceAsStream(IMAGE_DIR + baseName + ext)) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Tách cả nền đen thuần (#000000) và nền trắng thuần (#FFFFFF) lan từ 4 cạnh ảnh vào trong,
     * làm mềm viền răng cưa theo màu nền và cắt gọn sát vùng có hình ảnh.
     */
    public static Image stripOuterBlackAndCrop(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        if (width <= 0 || height <= 0) {
            return source;
        }

        PixelReader reader = source.getPixelReader();
        if (reader == null) {
            return source;
        }

        int cornerAlpha = (reader.getArgb(0, 0) >>> 24) & 0xFF;
        boolean hasExistingTransparency = cornerAlpha < 240;

        int total = width * height;
        int[] argb = new int[total];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = reader.getArgb(x, y);
            }
        }

        if (!hasExistingTransparency) {
            byte[] bgType = new byte[total];
            ArrayDeque<Integer> queue = new ArrayDeque<>();

            for (int x = 0; x < width; x++) {
                tryEnqueueBorder(x, 0, width, argb, bgType, queue);
                tryEnqueueBorder(x, height - 1, width, argb, bgType, queue);
            }
            for (int y = 0; y < height; y++) {
                tryEnqueueBorder(0, y, width, argb, bgType, queue);
                tryEnqueueBorder(width - 1, y, width, argb, bgType, queue);
            }

            while (!queue.isEmpty()) {
                int idx = queue.poll();
                byte type = bgType[idx];
                int x = idx % width;
                int y = idx / width;
                if (x > 0) tryEnqueueNeighbor(x - 1, y, width, type, argb, bgType, queue);
                if (x + 1 < width) tryEnqueueNeighbor(x + 1, y, width, type, argb, bgType, queue);
                if (y > 0) tryEnqueueNeighbor(x, y - 1, width, type, argb, bgType, queue);
                if (y + 1 < height) tryEnqueueNeighbor(x, y + 1, width, type, argb, bgType, queue);
            }

            for (int i = 0; i < total; i++) {
                if (bgType[i] != BG_NONE) {
                    argb[i] = 0x00000000;
                } else {
                    int x = i % width;
                    int y = i / width;
                    boolean touchesBlack = (x > 0 && bgType[i - 1] == BG_BLACK)
                            || (x + 1 < width && bgType[i + 1] == BG_BLACK)
                            || (y > 0 && bgType[i - width] == BG_BLACK)
                            || (y + 1 < height && bgType[i + width] == BG_BLACK);
                    boolean touchesWhite = (x > 0 && bgType[i - 1] == BG_WHITE)
                            || (x + 1 < width && bgType[i + 1] == BG_WHITE)
                            || (y > 0 && bgType[i - width] == BG_WHITE)
                            || (y + 1 < height && bgType[i + width] == BG_WHITE);

                    int c = argb[i];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    if (touchesBlack) {
                        int maxCh = Math.max(r, Math.max(g, b));
                        if (maxCh < 22) {
                            argb[i] = 0x00000000;
                        } else if (maxCh < 44) {
                            int softAlpha = Math.max(0, Math.min(255, (maxCh - 20) * 10));
                            argb[i] = (softAlpha << 24) | (c & 0x00FFFFFF);
                        }
                    } else if (touchesWhite) {
                        int minCh = Math.min(r, Math.min(g, b));
                        if (minCh > 236) {
                            argb[i] = 0x00000000;
                        } else if (minCh > 212) {
                            int softAlpha = Math.max(0, Math.min(255, (238 - minCh) * 10));
                            argb[i] = (softAlpha << 24) | (c & 0x00FFFFFF);
                        }
                    }
                }
            }
        }

        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (argb[y * width + x] >>> 24) & 0xFF;
                if (alpha > 12) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }

        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        WritableImage output = new WritableImage(cropW, cropH);
        PixelWriter writer = output.getPixelWriter();
        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                writer.setArgb(x, y, argb[(minY + y) * width + (minX + x)]);
            }
        }
        return output;
    }

    private static void tryEnqueueBorder(int x, int y, int width, int[] argb, byte[] bgType, ArrayDeque<Integer> queue) {
        int idx = y * width + x;
        if (bgType[idx] != BG_NONE) {
            return;
        }
        int c = argb[idx];
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        if (r <= 14 && g <= 14 && b <= 14) {
            bgType[idx] = BG_BLACK;
            queue.add(idx);
        } else if (r >= 240 && g >= 240 && b >= 240) {
            bgType[idx] = BG_WHITE;
            queue.add(idx);
        }
    }

    private static void tryEnqueueNeighbor(
            int x, int y, int width, byte targetType, int[] argb, byte[] bgType, ArrayDeque<Integer> queue) {
        int idx = y * width + x;
        if (bgType[idx] != BG_NONE) {
            return;
        }
        int c = argb[idx];
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        if (targetType == BG_BLACK && r <= 14 && g <= 14 && b <= 14) {
            bgType[idx] = BG_BLACK;
            queue.add(idx);
        } else if (targetType == BG_WHITE && r >= 240 && g >= 240 && b >= 240) {
            bgType[idx] = BG_WHITE;
            queue.add(idx);
        }
    }
}