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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * An RGBA image that polygons are filled into with anti-aliasing, encoded as PNG, without AWT so
 * that printing the banner neither loads a graphics environment nor needs one in a native image.
 *
 * <p>Each pixel row is sampled on {@value #SUBSAMPLES} scanlines and the coverage of every span
 * between edge crossings is exact horizontally, so that both fill rules hold and edges are smooth.</p>
 */
final class SvgRaster {

    private static final int SUBSAMPLES = 5;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final int width;
    private final int height;
    // premultiplied red, green, blue and alpha in [0, 1]
    private final float[] pixels;

    SvgRaster(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Empty raster " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.pixels = new float[width * height * 4];
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    /**
     * Fills polygons with a color.
     *
     * @param polygons Closed polygons as alternating x and y device coordinates
     * @param evenOdd Whether to use the even-odd rule rather than non-zero
     * @param argb The color, its alpha ignored
     * @param alpha The opacity in [0, 1]
     * @param mask The coverage of the clip per pixel, null when not clipped
     */
    void fill(List<double[]> polygons, boolean evenOdd, int argb, double alpha, float @Nullable [] mask) {
        if (alpha <= 0) {
            return;
        }
        float red = ((argb >> 16) & 0xFF) / 255f;
        float green = ((argb >> 8) & 0xFF) / 255f;
        float blue = (argb & 0xFF) / 255f;
        rasterize(polygons, evenOdd, (y, left, coverage, count) -> {
            int index = y * width + left;
            for (int i = 0; i < count; i++, index++) {
                float cover = Math.min(coverage[i], 1f);
                if (cover > 0) {
                    blend(index, red, green, blue, (float) (cover * alpha * (mask == null ? 1 : mask[index])));
                }
            }
        });
    }

    /**
     * Adds the coverage of polygons to a mask, the union of the shapes of a clip path.
     *
     * @param polygons Closed polygons as alternating x and y device coordinates
     * @param evenOdd Whether to use the even-odd rule rather than non-zero
     * @param mask The coverage per pixel
     */
    void coverage(List<double[]> polygons, boolean evenOdd, float[] mask) {
        rasterize(polygons, evenOdd, (y, left, coverage, count) -> {
            int index = y * width + left;
            for (int i = 0; i < count; i++, index++) {
                mask[index] = Math.max(mask[index], Math.min(coverage[i], 1f));
            }
        });
    }

    /**
     * Draws an image, sampling each device pixel enough times to average the image pixels it covers.
     *
     * @param image The image
     * @param matrix The transform from image pixels to device pixels
     * @param alpha The opacity in [0, 1]
     * @param mask The coverage of the clip per pixel, null when not clipped
     */
    void drawImage(PngImage image, double[] matrix, double alpha, float @Nullable [] mask) {
        double determinant = matrix[0] * matrix[3] - matrix[1] * matrix[2];
        if (determinant == 0 || alpha <= 0) {
            return;
        }
        double[] inverse = {
            matrix[3] / determinant, -matrix[1] / determinant, -matrix[2] / determinant, matrix[0] / determinant,
            (matrix[2] * matrix[5] - matrix[3] * matrix[4]) / determinant,
            (matrix[1] * matrix[4] - matrix[0] * matrix[5]) / determinant
        };
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            double ix = (corner & 1) == 0 ? 0 : image.width();
            double iy = (corner & 2) == 0 ? 0 : image.height();
            double x = matrix[0] * ix + matrix[2] * iy + matrix[4];
            double y = matrix[1] * ix + matrix[3] * iy + matrix[5];
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        int left = (int) Math.max(0, Math.floor(minX));
        int right = (int) Math.min(width, Math.ceil(maxX));
        int top = (int) Math.max(0, Math.floor(minY));
        int bottom = (int) Math.min(height, Math.ceil(maxY));
        // image pixels per device pixel decides the samples per device pixel
        double density = Math.sqrt(Math.abs(1 / determinant));
        int samples = (int) Math.max(1, Math.min(8, Math.ceil(density)));
        float weight = 1f / (samples * samples);
        int[] pixels = image.argb();
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                float red = 0;
                float green = 0;
                float blue = 0;
                float a = 0;
                for (int sy = 0; sy < samples; sy++) {
                    double dy = y + (sy + 0.5) / samples;
                    for (int sx = 0; sx < samples; sx++) {
                        double dx = x + (sx + 0.5) / samples;
                        int ix = (int) Math.floor(inverse[0] * dx + inverse[2] * dy + inverse[4]);
                        int iy = (int) Math.floor(inverse[1] * dx + inverse[3] * dy + inverse[5]);
                        if (ix < 0 || iy < 0 || ix >= image.width() || iy >= image.height()) {
                            continue;
                        }
                        int pixel = pixels[iy * image.width() + ix];
                        float pixelAlpha = (pixel >>> 24) / 255f * weight;
                        red += ((pixel >> 16) & 0xFF) / 255f * pixelAlpha;
                        green += ((pixel >> 8) & 0xFF) / 255f * pixelAlpha;
                        blue += (pixel & 0xFF) / 255f * pixelAlpha;
                        a += pixelAlpha;
                    }
                }
                if (a <= 0) {
                    continue;
                }
                int index = y * width + x;
                float coverage = (float) (a * alpha * (mask == null ? 1 : mask[index]));
                blend(index, red / a, green / a, blue / a, coverage);
            }
        }
    }

    private void blend(int index, float red, float green, float blue, float a) {
        if (a <= 0) {
            return;
        }
        int offset = index * 4;
        float keep = 1 - a;
        pixels[offset] = red * a + pixels[offset] * keep;
        pixels[offset + 1] = green * a + pixels[offset + 1] * keep;
        pixels[offset + 2] = blue * a + pixels[offset + 2] * keep;
        pixels[offset + 3] = a + pixels[offset + 3] * keep;
    }

    /**
     * Receives the coverage of a pixel row.
     */
    @FunctionalInterface
    private interface RowSink {
        void row(int y, int left, float[] coverage, int count);
    }

    private void rasterize(List<double[]> polygons, boolean evenOdd, RowSink sink) {
        if (polygons.isEmpty()) {
            return;
        }
        int edgeCount = 0;
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] polygon : polygons) {
            edgeCount += polygon.length / 2;
            for (int i = 0; i < polygon.length; i += 2) {
                minX = Math.min(minX, polygon[i]);
                maxX = Math.max(maxX, polygon[i]);
                minY = Math.min(minY, polygon[i + 1]);
                maxY = Math.max(maxY, polygon[i + 1]);
            }
        }
        int top = (int) Math.max(0, Math.floor(minY));
        int bottom = (int) Math.min(height, Math.ceil(maxY));
        int left = (int) Math.max(0, Math.floor(minX));
        int right = (int) Math.min(width, Math.ceil(maxX) + 1);
        if (top >= bottom || left >= right) {
            return;
        }
        // edges as x0, y0, x1, y1, direction with y0 < y1, bucketed by the rows they cross
        double[] edges = new double[edgeCount * 5];
        int edges5 = 0;
        for (double[] polygon : polygons) {
            int n = polygon.length / 2;
            for (int i = 0; i < n; i++) {
                double ax = polygon[2 * i];
                double ay = polygon[2 * i + 1];
                double bx = polygon[2 * ((i + 1) % n)];
                double by = polygon[2 * ((i + 1) % n) + 1];
                if (ay == by) {
                    continue;
                }
                boolean down = ay < by;
                edges[edges5++] = down ? ax : bx;
                edges[edges5++] = down ? ay : by;
                edges[edges5++] = down ? bx : ax;
                edges[edges5++] = down ? by : ay;
                edges[edges5++] = down ? 1 : -1;
            }
        }
        int rows = bottom - top;
        int[] rowCounts = new int[rows + 1];
        for (int e = 0; e < edges5; e += 5) {
            int first = Math.max(0, (int) Math.floor(edges[e + 1]) - top);
            int last = Math.min(rows - 1, (int) Math.ceil(edges[e + 3]) - top - 1);
            for (int row = first; row <= last; row++) {
                rowCounts[row + 1]++;
            }
        }
        for (int row = 0; row < rows; row++) {
            rowCounts[row + 1] += rowCounts[row];
        }
        int[] rowEdges = new int[rowCounts[rows]];
        int[] fillPosition = Arrays.copyOf(rowCounts, rows);
        for (int e = 0; e < edges5; e += 5) {
            int first = Math.max(0, (int) Math.floor(edges[e + 1]) - top);
            int last = Math.min(rows - 1, (int) Math.ceil(edges[e + 3]) - top - 1);
            for (int row = first; row <= last; row++) {
                rowEdges[fillPosition[row]++] = e;
            }
        }

        float[] coverage = new float[right - left + 1];
        double[] crossings = new double[16];
        int[] directions = new int[16];
        float weight = 1f / SUBSAMPLES;
        for (int row = 0; row < rows; row++) {
            int y = top + row;
            int from = rowCounts[row];
            int to = rowCounts[row + 1];
            if (from == to) {
                continue;
            }
            Arrays.fill(coverage, 0);
            if (to - from > crossings.length) {
                crossings = new double[(to - from) * 2];
                directions = new int[(to - from) * 2];
            }
            for (int sample = 0; sample < SUBSAMPLES; sample++) {
                double sampleY = y + (sample + 0.5) / SUBSAMPLES;
                int count = 0;
                for (int k = from; k < to; k++) {
                    int e = rowEdges[k];
                    double y0 = edges[e + 1];
                    double y1 = edges[e + 3];
                    if (sampleY >= y0 && sampleY < y1) {
                        double x0 = edges[e];
                        double x1 = edges[e + 2];
                        double x = x0 + (sampleY - y0) * (x1 - x0) / (y1 - y0);
                        int direction = (int) edges[e + 4];
                        // insertion sort, rows cross few edges
                        int position = count++;
                        while (position > 0 && crossings[position - 1] > x) {
                            crossings[position] = crossings[position - 1];
                            directions[position] = directions[position - 1];
                            position--;
                        }
                        crossings[position] = x;
                        directions[position] = direction;
                    }
                }
                int winding = 0;
                for (int i = 0; i + 1 < count; i++) {
                    winding += directions[i];
                    boolean inside = evenOdd ? (winding & 1) != 0 : winding != 0;
                    if (inside) {
                        span(coverage, crossings[i] - left, crossings[i + 1] - left, weight);
                    }
                }
            }
            sink.row(y, left, coverage, Math.min(right, width) - left);
        }
    }

    private static void span(float[] coverage, double start, double end, float weight) {
        double from = Math.max(0, start);
        double to = Math.min(coverage.length - 1.0, end);
        if (to <= from) {
            return;
        }
        int first = (int) from;
        int last = (int) to;
        if (first == last) {
            coverage[first] += (float) ((to - from) * weight);
            return;
        }
        coverage[first] += (float) ((first + 1 - from) * weight);
        for (int i = first + 1; i < last; i++) {
            coverage[i] += weight;
        }
        coverage[last] += (float) ((to - last) * weight);
    }

    /**
     * @return The image as a PNG with straight alpha
     */
    byte[] toPng() {
        byte[] raw = new byte[height * (width * 4 + 1)];
        int position = 0;
        int pixel = 0;
        for (int y = 0; y < height; y++) {
            // filter type None
            raw[position++] = 0;
            for (int x = 0; x < width; x++, pixel += 4) {
                float a = pixels[pixel + 3];
                if (a <= 0) {
                    position += 4;
                    continue;
                }
                raw[position++] = channel(pixels[pixel] / a);
                raw[position++] = channel(pixels[pixel + 1] / a);
                raw[position++] = channel(pixels[pixel + 2] / a);
                raw[position++] = channel(a);
            }
        }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream(raw.length / 8 + 64);
            png.write(PNG_SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream(13);
            DataOutputStream headerData = new DataOutputStream(header);
            headerData.writeInt(width);
            headerData.writeInt(height);
            // 8 bits per channel, RGBA, deflate, no filtering method extensions, no interlace
            headerData.write(new byte[] {8, 6, 0, 0, 0});
            chunk(png, "IHDR", header.toByteArray());
            ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 8 + 64);
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(compressed, deflater)) {
                deflate.write(raw);
            } finally {
                deflater.end();
            }
            chunk(png, "IDAT", compressed.toByteArray());
            chunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte channel(float value) {
        return (byte) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    private static void chunk(ByteArrayOutputStream png, String type, byte[] data) throws IOException {
        DataOutputStream out = new DataOutputStream(png);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
