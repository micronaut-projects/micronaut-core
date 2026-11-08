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

import java.util.List;

/**
 * Outlines strokes as polygons that cover them: a quadrilateral per segment, a disc at every join
 * and the caps. Every polygon winds the same way, so filling them together with the non-zero rule
 * draws their union.
 */
final class Stroker {

    static final int BUTT = 0;
    static final int ROUND = 1;
    static final int SQUARE = 2;

    /**
     * The length of the chords of the discs in device pixels.
     */
    static final double CHORD = 1.5;

    private Stroker() {
    }

    /**
     * Adds the outline of a stroked polyline.
     *
     * @param points The polyline as alternating x and y device coordinates
     * @param closed Whether the polyline is a closed subpath, joined at its start rather than capped
     * @param halfWidth Half the stroke width in device pixels
     * @param lineCap The cap of open polylines
     * @param chord The length of the chords of the discs at joins and round caps
     * @param polygons The list to add the polygons to
     */
    static void stroke(double[] points, boolean closed, double halfWidth, int lineCap, double chord, List<double[]> polygons) {
        if (halfWidth <= 0 || points.length < 2) {
            return;
        }
        double[] unique = withoutRepeatedPoints(points);
        int count = unique.length / 2;
        if (closed && count > 1 && unique[0] == unique[unique.length - 2] && unique[1] == unique[unique.length - 1]) {
            count--;
        }
        if (count == 1) {
            if (lineCap == ROUND) {
                polygons.add(disc(unique[0], unique[1], halfWidth, chord));
            } else if (lineCap == SQUARE) {
                polygons.add(oriented(new double[] {
                    unique[0] - halfWidth, unique[1] - halfWidth, unique[0] + halfWidth, unique[1] - halfWidth,
                    unique[0] + halfWidth, unique[1] + halfWidth, unique[0] - halfWidth, unique[1] + halfWidth}));
            }
            return;
        }
        int segments = closed ? count : count - 1;
        for (int i = 0; i < segments; i++) {
            double ax = unique[2 * i];
            double ay = unique[2 * i + 1];
            double bx = unique[2 * ((i + 1) % count)];
            double by = unique[2 * ((i + 1) % count) + 1];
            double length = Math.hypot(bx - ax, by - ay);
            if (length == 0) {
                continue;
            }
            double ux = (bx - ax) / length;
            double uy = (by - ay) / length;
            if (!closed && lineCap == SQUARE) {
                if (i == 0) {
                    ax -= ux * halfWidth;
                    ay -= uy * halfWidth;
                }
                if (i == segments - 1) {
                    bx += ux * halfWidth;
                    by += uy * halfWidth;
                }
            }
            double nx = -uy * halfWidth;
            double ny = ux * halfWidth;
            polygons.add(oriented(new double[] {ax + nx, ay + ny, bx + nx, by + ny, bx - nx, by - ny, ax - nx, ay - ny}));
        }
        for (int i = 0; i < count; i++) {
            boolean end = i == 0 || i == count - 1;
            if (!end || closed || lineCap == ROUND) {
                polygons.add(disc(unique[2 * i], unique[2 * i + 1], halfWidth, chord));
            }
        }
    }

    private static double[] withoutRepeatedPoints(double[] points) {
        double[] unique = new double[points.length];
        int size = 0;
        for (int i = 0; i + 1 < points.length; i += 2) {
            if (size == 0 || Math.abs(points[i] - unique[size - 2]) > 1e-9 || Math.abs(points[i + 1] - unique[size - 1]) > 1e-9) {
                unique[size++] = points[i];
                unique[size++] = points[i + 1];
            }
        }
        double[] result = new double[size];
        System.arraycopy(unique, 0, result, 0, size);
        return result;
    }

    private static double[] disc(double cx, double cy, double radius, double chord) {
        int n = (int) Math.max(8, Math.min(64, Math.ceil(2 * Math.PI * radius / chord)));
        double[] polygon = new double[n * 2];
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n;
            polygon[2 * i] = cx + radius * Math.cos(angle);
            polygon[2 * i + 1] = cy + radius * Math.sin(angle);
        }
        return polygon;
    }

    /**
     * @return The polygon wound so that its signed area is positive, like the discs
     */
    private static double[] oriented(double[] polygon) {
        double area = 0;
        int n = polygon.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += polygon[2 * i] * polygon[2 * j + 1] - polygon[2 * j] * polygon[2 * i + 1];
        }
        if (area >= 0) {
            return polygon;
        }
        double[] reversed = new double[polygon.length];
        for (int i = 0; i < n; i++) {
            reversed[2 * i] = polygon[2 * (n - 1 - i)];
            reversed[2 * i + 1] = polygon[2 * (n - 1 - i) + 1];
        }
        return reversed;
    }
}
