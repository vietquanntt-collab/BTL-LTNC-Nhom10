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
 * Tải tài nguyên hình ảnh cho sân khấu 2D, tự động tách nền đen viền ngoài
 * và tự khớp hình minh họa vật phẩm khi dữ liệu chưa đính kèm Base64.
 */
public final class StageAssetLoader {

    private static final String IMAGE_DIR = "/com/btl/team10/client/images/";
    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

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
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
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
     * Tách nền đen thuần (#000000) lan từ 4 cạnh ảnh vào trong (giữ nguyên vùng tối bên trong vật thể)
     * và tự cắt sát vùng có hình ảnh.
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

        // Nếu ảnh đã có kênh alpha trong suốt sẵn ở góc thì giữ nguyên, chỉ cắt sát khung
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
            boolean[] bgMask = new boolean[total];
            ArrayDeque<Integer> queue = new ArrayDeque<>();

            for (int x = 0; x < width; x++) {
                tryEnqueueBlack(x, 0, width, argb, bgMask, queue);
                tryEnqueueBlack(x, height - 1, width, argb, bgMask, queue);
            }
            for (int y = 0; y < height; y++) {
                tryEnqueueBlack(0, y, width, argb, bgMask, queue);
                tryEnqueueBlack(width - 1, y, width, argb, bgMask, queue);
            }

            while (!queue.isEmpty()) {
                int idx = queue.poll();
                int x = idx % width;
                int y = idx / width;
                if (x > 0) tryEnqueueBlack(x - 1, y, width, argb, bgMask, queue);
                if (x + 1 < width) tryEnqueueBlack(x + 1, y, width, argb, bgMask, queue);
                if (y > 0) tryEnqueueBlack(x, y - 1, width, argb, bgMask, queue);
                if (y + 1 < height) tryEnqueueBlack(x, y + 1, width, argb, bgMask, queue);
            }

            for (int i = 0; i < total; i++) {
                if (bgMask[i]) {
                    argb[i] = 0x00000000;
                } else {
                    int x = i % width;
                    int y = i / width;
                    boolean touchesBg = (x > 0 && bgMask[i - 1])
                            || (x + 1 < width && bgMask[i + 1])
                            || (y > 0 && bgMask[i - width])
                            || (y + 1 < height && bgMask[i + width]);
                    if (touchesBg) {
                        int c = argb[i];
                        int maxCh = Math.max((c >> 16) & 0xFF, Math.max((c >> 8) & 0xFF, c & 0xFF));
                        if (maxCh < 28) {
                            argb[i] = 0x00000000;
                        } else if (maxCh < 52) {
                            int softAlpha = Math.min(255, (maxCh - 24) * 9);
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

    private static void tryEnqueueBlack(int x, int y, int width, int[] argb, boolean[] bgMask, ArrayDeque<Integer> queue) {
        int idx = y * width + x;
        if (bgMask[idx]) {
            return;
        }
        int c = argb[idx];
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        if (r <= 14 && g <= 14 && b <= 14) {
            bgMask[idx] = true;
            queue.add(idx);
        }
    }
}
