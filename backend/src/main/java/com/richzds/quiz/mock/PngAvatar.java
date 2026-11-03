package com.richzds.quiz.mock;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 几何图案头像：5×5 左右对称的格子（像 GitHub 默认头像），直接编码成 PNG，不依赖 AWT，服务器上的精简 JRE 也能跑。
 */
final class PngAvatar {

    static final int SIZE = 120;
    private static final int CELLS = 5;
    private static final int MARGIN = 10;

    private PngAvatar() {
    }

    static byte[] generate(Random random) {
        float hue = random.nextFloat();
        int fg = hsv(hue, 0.45f + random.nextFloat() * 0.3f, 0.65f + random.nextFloat() * 0.25f);
        int bg = hsv((hue + 0.5f) % 1f, 0.06f, 0.97f);
        boolean[][] on = new boolean[CELLS][CELLS];
        for (int y = 0; y < CELLS; y++) {
            for (int x = 0; x <= CELLS / 2; x++) {
                boolean v = random.nextBoolean();
                on[y][x] = v;
                on[y][CELLS - 1 - x] = v;
            }
        }
        int cell = (SIZE - 2 * MARGIN) / CELLS;
        byte[] raw = new byte[SIZE * (1 + SIZE * 3)];
        int i = 0;
        for (int y = 0; y < SIZE; y++) {
            raw[i++] = 0; // 每行的过滤方式：不过滤
            for (int x = 0; x < SIZE; x++) {
                int cx = (x - MARGIN) / cell;
                int cy = (y - MARGIN) / cell;
                boolean inside = x >= MARGIN && y >= MARGIN && cx < CELLS && cy < CELLS;
                int c = inside && on[cy][cx] ? fg : bg;
                raw[i++] = (byte) (c >> 16);
                raw[i++] = (byte) (c >> 8);
                raw[i++] = (byte) c;
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(SIZE).putInt(SIZE);
        ihdr.put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0); // 8 位 RGB
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IDAT", deflate(raw));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(t);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6) % 2 - 1));
        float m = v - c;
        float[] rgb = switch ((int) (h * 6) % 6) {
            case 0 -> new float[] {c, x, 0};
            case 1 -> new float[] {x, c, 0};
            case 2 -> new float[] {0, c, x};
            case 3 -> new float[] {0, x, c};
            case 4 -> new float[] {x, 0, c};
            default -> new float[] {c, 0, x};
        };
        int r = Math.round((rgb[0] + m) * 255);
        int g = Math.round((rgb[1] + m) * 255);
        int b = Math.round((rgb[2] + m) * 255);
        return (r << 16) | (g << 8) | b;
    }
}
