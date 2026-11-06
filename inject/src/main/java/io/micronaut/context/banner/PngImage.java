/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.banner;

import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * A decoded PNG, for the {@code image} elements of SVG banners, read without AWT.
 *
 * <p>Every color type is supported at bit depths up to 8, and 16-bit images are reduced to 8 bits.
 * Interlaced images are not supported.</p>
 */
final class PngImage {

    private static final long SIGNATURE = 0x89504E470D0A1A0AL;
    private static final int MAX_PIXELS = 8192 * 8192;
    private static final int GRAY = 0;
    private static final int RGB = 2;
    private static final int PALETTE = 3;
    private static final int GRAY_ALPHA = 4;
    private static final int RGBA = 6;

    private final int width;
    private final int height;
    private final int[] argb;

    private PngImage(int width, int height, int[] argb) {
        this.width = width;
        this.height = height;
        this.argb = argb;
    }

    /**
     * @return The width
     */
    int width() {
        return width;
    }

    /**
     * @return The height
     */
    int height() {
        return height;
    }

    /**
     * @return The pixels, row by row, with straight alpha
     */
    int[] argb() {
        return argb;
    }

    /**
     * Decodes a PNG.
     *
     * @param png The PNG
     * @return The image
     * @throws IllegalArgumentException If the data is not a PNG this decoder supports
     */
    static PngImage decode(byte[] png) {
        ByteBuffer buffer = ByteBuffer.wrap(png);
        if (png.length < 8 || buffer.getLong() != SIGNATURE) {
            throw new IllegalArgumentException("Not a PNG");
        }
        int width = 0;
        int height = 0;
        int depth = 0;
        int colorType = 0;
        int[] palette = new int[0];
        int @Nullable [] transparency = null;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        while (buffer.remaining() >= 12) {
            int length = buffer.getInt();
            byte[] typeBytes = new byte[4];
            buffer.get(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            if (length < 0 || length > buffer.remaining() - 4) {
                throw new IllegalArgumentException("Truncated PNG chunk " + type);
            }
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            buffer.getInt(); // the CRC is not checked
            switch (type) {
                case "IHDR" -> {
                    ByteBuffer header = ByteBuffer.wrap(chunk);
                    width = header.getInt();
                    height = header.getInt();
                    depth = header.get() & 0xFF;
                    colorType = header.get() & 0xFF;
                    header.get();
                    header.get();
                    if ((header.get() & 0xFF) != 0) {
                        throw new IllegalArgumentException("Interlaced PNGs are not supported");
                    }
                }
                case "PLTE" -> {
                    palette = new int[length / 3];
                    for (int i = 0; i < palette.length; i++) {
                        palette[i] = 0xFF000000 | (chunk[3 * i] & 0xFF) << 16 | (chunk[3 * i + 1] & 0xFF) << 8 | chunk[3 * i + 2] & 0xFF;
                    }
                }
                case "tRNS" -> {
                    transparency = new int[length];
                    for (int i = 0; i < length; i++) {
                        transparency[i] = chunk[i] & 0xFF;
                    }
                }
                case "IDAT" -> data.writeBytes(chunk);
                case "IEND" -> buffer.position(buffer.limit());
                default -> {
                    // ancillary chunks are ignored
                }
            }
        }
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Unsupported PNG size " + width + "x" + height);
        }
        int channels = switch (colorType) {
            case GRAY, PALETTE -> 1;
            case GRAY_ALPHA -> 2;
            case RGB -> 3;
            case RGBA -> 4;
            default -> throw new IllegalArgumentException("Unknown PNG color type " + colorType);
        };
        if (depth != 1 && depth != 2 && depth != 4 && depth != 8 && depth != 16) {
            throw new IllegalArgumentException("Unknown PNG bit depth " + depth);
        }
        int bitsPerPixel = channels * depth;
        int stride = (width * bitsPerPixel + 7) / 8;
        int bytesPerPixel = Math.max(1, bitsPerPixel / 8);
        byte[] raw = inflate(data.toByteArray(), height * (stride + 1));
        byte[] previous = new byte[stride];
        byte[] row = new byte[stride];
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * (stride + 1);
            int filter = raw[offset] & 0xFF;
            System.arraycopy(raw, offset + 1, row, 0, stride);
            unfilter(filter, row, previous, bytesPerPixel);
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = pixel(row, x, colorType, depth, palette, transparency);
            }
            byte[] swap = previous;
            previous = row;
            row = swap;
        }
        return new PngImage(width, height, argb);
    }

    private static byte[] inflate(byte[] compressed, int size) {
        try (Inflater inflater = new Inflater()) {
            inflater.setInput(compressed);
            byte[] raw = new byte[size];
            int read = 0;
            while (read < size && !inflater.finished()) {
                int n = inflater.inflate(raw, read, size - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                read += n;
            }
            if (read < size) {
                throw new IllegalArgumentException("Truncated PNG data");
            }
            return raw;
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("Corrupt PNG data", e);
        }
    }

    private static void unfilter(int filter, byte[] row, byte[] previous, int bpp) {
        for (int i = 0; i < row.length; i++) {
            int left = i >= bpp ? row[i - bpp] & 0xFF : 0;
            int up = previous[i] & 0xFF;
            int upLeft = i >= bpp ? previous[i - bpp] & 0xFF : 0;
            int predictor = switch (filter) {
                case 0 -> 0;
                case 1 -> left;
                case 2 -> up;
                case 3 -> (left + up) >>> 1;
                case 4 -> paeth(left, up, upLeft);
                default -> throw new IllegalArgumentException("Unknown PNG filter " + filter);
            };
            row[i] = (byte) (row[i] + predictor);
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a);
        int pb = Math.abs(p - b);
        int pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) {
            return a;
        }
        return pb <= pc ? b : c;
    }

    private static int pixel(byte[] row, int x, int colorType, int depth, int[] palette, int @Nullable [] transparency) {
        return switch (colorType) {
            case PALETTE -> {
                int index = sample(row, x, depth);
                int color = index < palette.length ? palette[index] : 0xFF000000;
                int alpha = transparency != null && index < transparency.length ? transparency[index] : 0xFF;
                yield alpha << 24 | color & 0xFFFFFF;
            }
            case GRAY -> {
                int full = depth == 16 ? (row[2 * x] & 0xFF) << 8 | row[2 * x + 1] & 0xFF : sample(row, x, depth);
                int gray = depth == 16 ? full >>> 8 : full * 255 / ((1 << depth) - 1);
                boolean transparent = transparency != null && transparency.length >= 2
                    && full == (transparency[0] << 8 | transparency[1]);
                yield (transparent ? 0 : 0xFF000000) | gray << 16 | gray << 8 | gray;
            }
            case GRAY_ALPHA -> {
                int gray = channel(row, x * 2, depth);
                yield channel(row, x * 2 + 1, depth) << 24 | gray << 16 | gray << 8 | gray;
            }
            case RGB -> 0xFF000000 | channel(row, x * 3, depth) << 16 | channel(row, x * 3 + 1, depth) << 8 | channel(row, x * 3 + 2, depth);
            default -> channel(row, x * 4 + 3, depth) << 24 | channel(row, x * 4, depth) << 16
                | channel(row, x * 4 + 1, depth) << 8 | channel(row, x * 4 + 2, depth);
        };
    }

    /**
     * @return A sample of a one channel image, which may be packed below 8 bits; 16-bit samples keep their high byte
     */
    private static int sample(byte[] row, int x, int depth) {
        return switch (depth) {
            case 8 -> row[x] & 0xFF;
            case 16 -> row[2 * x] & 0xFF;
            default -> {
                int perByte = 8 / depth;
                int shift = 8 - depth * (x % perByte + 1);
                yield (row[x / perByte] >> shift) & ((1 << depth) - 1);
            }
        };
    }

    /**
     * @return An 8-bit sample of a multi channel image, which is 8 or 16 bits deep
     */
    private static int channel(byte[] row, int index, int depth) {
        return depth == 16 ? row[2 * index] & 0xFF : row[index] & 0xFF;
    }
}
