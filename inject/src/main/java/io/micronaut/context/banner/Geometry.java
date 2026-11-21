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

import io.micronaut.context.banner.SvgImage.Element;
import io.micronaut.context.banner.SvgImage.NumberScanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The outline of a shape as subpaths of cubic Bézier segments, in the coordinates of its element.
 * Lines, quadratic curves and arcs are converted to cubics so that flattening handles one kind of
 * segment, and a line flattens to a single edge since its control points are on it.
 */
final class Geometry {

    // flattening tolerance in device pixels
    private static final double TOLERANCE = 0.2;
    private static final int MAX_SEGMENTS = 100;
    // the control point distance of a cubic approximating a quarter circle
    private static final double KAPPA = 0.5522847498307936;

    private static final String POLYGON = "polygon";

    /**
     * The elements with an outline.
     */
    static final Set<String> SHAPES = Set.of("path", Geometry.POLYGON, "polyline", "rect", "circle", "ellipse", "line");

    /**
     * Each subpath is the start point followed by six coordinates per cubic segment.
     */
    private final List<double[]> subpaths;
    private final List<Boolean> closed;
    private final boolean fillable;

    private Geometry(List<double[]> subpaths, List<Boolean> closed) {
        this(subpaths, closed, true);
    }

    private Geometry(List<double[]> subpaths, List<Boolean> closed, boolean fillable) {
        this.subpaths = subpaths;
        this.closed = closed;
        this.fillable = fillable;
    }

    /**
     * @param element The shape element
     * @param time The time, for animated geometry attributes
     * @return The outline of the shape
     */
    static Geometry of(Element element, double time) {
        return switch (element.name) {
            case "path" -> {
                String d = element.attributes.getOrDefault("d", "");
                yield element.geometry(d, () -> path(d));
            }
            case POLYGON, "polyline" -> {
                String points = element.attributes.getOrDefault("points", "");
                boolean polygon = POLYGON.equals(element.name);
                yield element.geometry(points, () -> polygon(SvgImage.numbers(points), polygon));
            }
            case "line" -> {
                Geometry line = polygon(new double[] {element.number("x1", time, 0), element.number("y1", time, 0),
                    element.number("x2", time, 0), element.number("y2", time, 0)}, false);
                yield new Geometry(line.subpaths, line.closed, false);
            }
            case "rect" -> {
                double x = element.number("x", time, 0);
                double y = element.number("y", time, 0);
                double w = element.number("width", time, 0);
                double h = element.number("height", time, 0);
                double rx = element.number("rx", time, -1);
                double ry = element.number("ry", time, -1);
                rx = rx < 0 ? Math.max(ry, 0) : rx;
                ry = ry < 0 ? rx : ry;
                yield rect(x, y, w, h, Math.min(rx, w / 2), Math.min(ry, h / 2));
            }
            case "circle" -> {
                double r = element.number("r", time, 0);
                yield ellipse(element.number("cx", time, 0), element.number("cy", time, 0), r, r);
            }
            case "ellipse" -> ellipse(element.number("cx", time, 0), element.number("cy", time, 0),
                element.number("rx", time, 0), element.number("ry", time, 0));
            default -> new Geometry(List.of(), List.of());
        };
    }

    /**
     * @return Whether the shape has an inside to fill, which a line does not
     */
    boolean fillable() {
        return fillable;
    }

    /**
     * Flattens the outline to closed polygons in device coordinates.
     *
     * @param matrix The transform to device coordinates
     * @return The polygons as alternating x and y coordinates
     */
    List<double[]> flatten(double[] matrix) {
        List<double[]> polygons = new ArrayList<>(subpaths.size());
        for (double[] subpath : subpaths) {
            double[] points = flatten(subpath, matrix);
            if (points.length > 4) {
                polygons.add(points);
            }
        }
        return polygons;
    }

    /**
     * Outlines the stroke of the shape. The outline is built in the coordinates of the element and then
     * transformed, so that a non-uniform scale or a skew shapes the stroke as it shapes the path.
     *
     * @param matrix The transform to device coordinates
     * @param width The stroke width in the coordinates of the element
     * @param lineCap The line cap
     * @return The polygons covering the stroke in device coordinates, to fill with the non-zero rule
     */
    List<double[]> stroke(double[] matrix, double width, int lineCap) {
        // the largest scale of the transform decides how finely the outline is flattened
        double scale = Math.max(Math.hypot(matrix[0], matrix[1]), Math.hypot(matrix[2], matrix[3]));
        if (scale <= 0) {
            return List.of();
        }
        double[] local = {scale, 0, 0, scale, 0, 0};
        List<double[]> outline = new ArrayList<>();
        for (int i = 0; i < subpaths.size(); i++) {
            // flattened in element coordinates magnified to device size, then shrunk back
            double[] points = flatten(subpaths.get(i), local);
            for (int k = 0; k < points.length; k++) {
                points[k] /= scale;
            }
            Stroker.stroke(points, closed.get(i), width / 2, lineCap, Stroker.CHORD / scale, outline);
        }
        for (double[] polygon : outline) {
            for (int k = 0; k + 1 < polygon.length; k += 2) {
                double x = polygon[k];
                double y = polygon[k + 1];
                polygon[k] = tx(matrix, x, y);
                polygon[k + 1] = ty(matrix, x, y);
            }
        }
        return outline;
    }

    private static double[] flatten(double[] subpath, double[] matrix) {
        Points points = new Points();
        double x0 = tx(matrix, subpath[0], subpath[1]);
        double y0 = ty(matrix, subpath[0], subpath[1]);
        points.add(x0, y0);
        for (int i = 2; i + 5 < subpath.length; i += 6) {
            double x1 = tx(matrix, subpath[i], subpath[i + 1]);
            double y1 = ty(matrix, subpath[i], subpath[i + 1]);
            double x2 = tx(matrix, subpath[i + 2], subpath[i + 3]);
            double y2 = ty(matrix, subpath[i + 2], subpath[i + 3]);
            double x3 = tx(matrix, subpath[i + 4], subpath[i + 5]);
            double y3 = ty(matrix, subpath[i + 4], subpath[i + 5]);
            double ddx = Math.max(Math.abs(x0 - 2 * x1 + x2), Math.abs(x1 - 2 * x2 + x3));
            double ddy = Math.max(Math.abs(y0 - 2 * y1 + y2), Math.abs(y1 - 2 * y2 + y3));
            double dd = Math.hypot(ddx, ddy);
            int n = (int) Math.min(MAX_SEGMENTS, Math.max(1, Math.ceil(Math.sqrt(0.75 * dd / TOLERANCE))));
            for (int k = 1; k <= n; k++) {
                double t = (double) k / n;
                double u = 1 - t;
                double a = u * u * u;
                double b = 3 * u * u * t;
                double c = 3 * u * t * t;
                double e = t * t * t;
                points.add(a * x0 + b * x1 + c * x2 + e * x3, a * y0 + b * y1 + c * y2 + e * y3);
            }
            x0 = x3;
            y0 = y3;
        }
        return points.toArray();
    }

    private static double tx(double[] m, double x, double y) {
        return m[0] * x + m[2] * y + m[4];
    }

    private static double ty(double[] m, double x, double y) {
        return m[1] * x + m[3] * y + m[5];
    }

    private static Geometry polygon(double[] coordinates, boolean closed) {
        Builder builder = new Builder();
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            if (i == 0) {
                builder.moveTo(coordinates[0], coordinates[1]);
            } else {
                builder.lineTo(coordinates[i], coordinates[i + 1]);
            }
        }
        if (closed) {
            builder.close();
        }
        return builder.build();
    }

    private static Geometry rect(double x, double y, double w, double h, double rx, double ry) {
        Builder builder = new Builder();
        if (w <= 0 || h <= 0) {
            return builder.build();
        }
        if (rx <= 0 || ry <= 0) {
            builder.moveTo(x, y);
            builder.lineTo(x + w, y);
            builder.lineTo(x + w, y + h);
            builder.lineTo(x, y + h);
            builder.close();
            return builder.build();
        }
        double kx = rx * KAPPA;
        double ky = ry * KAPPA;
        builder.moveTo(x + rx, y);
        builder.lineTo(x + w - rx, y);
        builder.cubicTo(x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry);
        builder.lineTo(x + w, y + h - ry);
        builder.cubicTo(x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h);
        builder.lineTo(x + rx, y + h);
        builder.cubicTo(x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry);
        builder.lineTo(x, y + ry);
        builder.cubicTo(x, y + ry - ky, x + rx - kx, y, x + rx, y);
        builder.close();
        return builder.build();
    }

    private static Geometry ellipse(double cx, double cy, double rx, double ry) {
        Builder builder = new Builder();
        if (rx <= 0 || ry <= 0) {
            return builder.build();
        }
        double kx = rx * KAPPA;
        double ky = ry * KAPPA;
        builder.moveTo(cx + rx, cy);
        builder.cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry);
        builder.cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy);
        builder.cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry);
        builder.cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy);
        builder.close();
        return builder.build();
    }

    /**
     * Parses path data.
     *
     * @param d The path data
     * @return The outline, up to the first error as SVG renders it
     */
    static Geometry path(String d) {
        Builder builder = new Builder();
        NumberScanner scanner = new NumberScanner(d);
        char command = 0;
        double x = 0;
        double y = 0;
        double startX = 0;
        double startY = 0;
        // the reflected control points of the S and T commands
        double lastCubicX = 0;
        double lastCubicY = 0;
        double lastQuadX = 0;
        double lastQuadY = 0;
        char previous = 0;
        try {
            while (!scanner.atEnd()) {
                Character next = scanner.nextCommand();
                if (next != null) {
                    command = next;
                } else if (command == 0 || command == 'Z' || command == 'z') {
                    // numbers without a command, or after a closepath, which takes none, are an error
                    break;
                } else if (command == 'M') {
                    command = 'L';
                } else if (command == 'm') {
                    command = 'l';
                }
                boolean relative = Character.isLowerCase(command);
                double ox = relative ? x : 0;
                double oy = relative ? y : 0;
                switch (Character.toUpperCase(command)) {
                    case 'M' -> {
                        x = ox + scanner.next();
                        y = oy + scanner.next();
                        startX = x;
                        startY = y;
                        builder.moveTo(x, y);
                    }
                    case 'L' -> {
                        x = ox + scanner.next();
                        y = oy + scanner.next();
                        builder.lineTo(x, y);
                    }
                    case 'H' -> {
                        x = ox + scanner.next();
                        builder.lineTo(x, y);
                    }
                    case 'V' -> {
                        y = oy + scanner.next();
                        builder.lineTo(x, y);
                    }
                    case 'C' -> {
                        double x1 = ox + scanner.next();
                        double y1 = oy + scanner.next();
                        lastCubicX = ox + scanner.next();
                        lastCubicY = oy + scanner.next();
                        x = ox + scanner.next();
                        y = oy + scanner.next();
                        builder.cubicTo(x1, y1, lastCubicX, lastCubicY, x, y);
                    }
                    case 'S' -> {
                        boolean smooth = "CcSs".indexOf(previous) >= 0;
                        double x1 = smooth ? 2 * x - lastCubicX : x;
                        double y1 = smooth ? 2 * y - lastCubicY : y;
                        lastCubicX = ox + scanner.next();
                        lastCubicY = oy + scanner.next();
                        x = ox + scanner.next();
                        y = oy + scanner.next();
                        builder.cubicTo(x1, y1, lastCubicX, lastCubicY, x, y);
                    }
                    case 'Q' -> {
                        lastQuadX = ox + scanner.next();
                        lastQuadY = oy + scanner.next();
                        double endX = ox + scanner.next();
                        double endY = oy + scanner.next();
                        builder.quadTo(x, y, lastQuadX, lastQuadY, endX, endY);
                        x = endX;
                        y = endY;
                    }
                    case 'T' -> {
                        boolean smooth = "QqTt".indexOf(previous) >= 0;
                        lastQuadX = smooth ? 2 * x - lastQuadX : x;
                        lastQuadY = smooth ? 2 * y - lastQuadY : y;
                        double endX = ox + scanner.next();
                        double endY = oy + scanner.next();
                        builder.quadTo(x, y, lastQuadX, lastQuadY, endX, endY);
                        x = endX;
                        y = endY;
                    }
                    case 'A' -> {
                        double rx = Math.abs(scanner.next());
                        double ry = Math.abs(scanner.next());
                        double rotation = scanner.next();
                        boolean largeArc = scanner.nextFlag();
                        boolean sweep = scanner.nextFlag();
                        double endX = ox + scanner.next();
                        double endY = oy + scanner.next();
                        builder.arcTo(rx, ry, rotation, largeArc, sweep, endX, endY);
                        x = endX;
                        y = endY;
                    }
                    case 'Z' -> {
                        x = startX;
                        y = startY;
                        builder.close();
                    }
                    default -> throw new IllegalArgumentException("Unknown path command " + command);
                }
                previous = command;
            }
        } catch (IllegalArgumentException e) {
            // an error ends the path, everything before it is still rendered
        }
        return builder.build();
    }

    private static final class Builder {
        private final List<double[]> subpaths = new ArrayList<>();
        private final List<Boolean> closed = new ArrayList<>();
        private Points current = new Points();
        private boolean currentClosed;
        private double lastX;
        private double lastY;

        void moveTo(double x, double y) {
            flush();
            current.add(x, y);
            lastX = x;
            lastY = y;
        }

        void lineTo(double x, double y) {
            ensureStarted();
            current.add(lastX, lastY);
            current.add(x, y);
            current.add(x, y);
            lastX = x;
            lastY = y;
        }

        void cubicTo(double x1, double y1, double x2, double y2, double x, double y) {
            ensureStarted();
            current.add(x1, y1);
            current.add(x2, y2);
            current.add(x, y);
            lastX = x;
            lastY = y;
        }

        void quadTo(double x0, double y0, double qx, double qy, double x, double y) {
            cubicTo(x0 + 2.0 / 3 * (qx - x0), y0 + 2.0 / 3 * (qy - y0), x + 2.0 / 3 * (qx - x), y + 2.0 / 3 * (qy - y), x, y);
        }

        /**
         * Adds an elliptical arc from the current point.
         */
        void arcTo(double rx, double ry, double rotation, boolean largeArc, boolean sweep, double x, double y) {
            double x0 = lastX;
            double y0 = lastY;
            if (x0 == x && y0 == y) {
                return;
            }
            if (rx == 0 || ry == 0) {
                lineTo(x, y);
                return;
            }
            // endpoint to center parameterization, SVG 1.1 appendix F.6.5
            double phi = Math.toRadians(rotation);
            double cos = Math.cos(phi);
            double sin = Math.sin(phi);
            double dx = (x0 - x) / 2;
            double dy = (y0 - y) / 2;
            double x1p = cos * dx + sin * dy;
            double y1p = -sin * dx + cos * dy;
            double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
            if (lambda > 1) {
                double root = Math.sqrt(lambda);
                rx *= root;
                ry *= root;
            }
            double numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
            double denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
            double coefficient = Math.sqrt(Math.max(0, numerator / denominator)) * (largeArc == sweep ? -1 : 1);
            double cxp = coefficient * rx * y1p / ry;
            double cyp = -coefficient * ry * x1p / rx;
            double cx = cos * cxp - sin * cyp + (x0 + x) / 2;
            double cy = sin * cxp + cos * cyp + (y0 + y) / 2;
            double theta1 = Math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx);
            double theta2 = Math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx);
            double delta = theta2 - theta1;
            if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            } else if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            }
            int segments = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2) - 1e-9);
            double step = delta / segments;
            double k = 4.0 / 3 * Math.tan(step / 4);
            double[] ellipse = {cx, cy, rx, ry, cos, sin};
            double theta = theta1;
            for (int i = 0; i < segments; i++) {
                double c1 = Math.cos(theta);
                double s1 = Math.sin(theta);
                double c2 = Math.cos(theta + step);
                double s2 = Math.sin(theta + step);
                double[] p1 = point(ellipse, c1 - k * s1, s1 + k * c1);
                double[] p2 = point(ellipse, c2 + k * s2, s2 - k * c2);
                double[] p3 = i == segments - 1 ? new double[] {x, y} : point(ellipse, c2, s2);
                cubicTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]);
                theta += step;
            }
        }

        /**
         * @param ellipse The center, radii, and the cosine and sine of the rotation of the ellipse
         * @param ux The x of the point on the unit circle
         * @param uy The y of the point on the unit circle
         * @return The point on the ellipse
         */
        private static double[] point(double[] ellipse, double ux, double uy) {
            double cx = ellipse[0];
            double cy = ellipse[1];
            double rx = ellipse[2];
            double ry = ellipse[3];
            double cos = ellipse[4];
            double sin = ellipse[5];
            return new double[] {cx + rx * ux * cos - ry * uy * sin, cy + rx * ux * sin + ry * uy * cos};
        }

        void close() {
            double startX = current.size > 0 ? current.values[0] : lastX;
            double startY = current.size > 0 ? current.values[1] : lastY;
            currentClosed = true;
            flush();
            moveTo(startX, startY);
        }

        private void ensureStarted() {
            if (current.size == 0) {
                current.add(lastX, lastY);
            }
        }

        private void flush() {
            if (current.size > 2) {
                subpaths.add(current.toArray());
                closed.add(currentClosed);
            }
            current = new Points();
            currentClosed = false;
        }

        Geometry build() {
            flush();
            return new Geometry(subpaths, closed);
        }
    }

    private static final class Points {
        private double[] values = new double[32];
        private int size;

        void add(double x, double y) {
            if (size + 2 > values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = x;
            values[size++] = y;
        }

        double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
