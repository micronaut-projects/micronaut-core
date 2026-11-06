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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * An SVG document reduced to what a banner needs: filled shapes, group transforms and opacity, and
 * the SMIL {@code <animate>} and {@code <animateTransform>} elements that change them over time.
 *
 * <p>Supported: {@code svg} (also nested, as a viewport), {@code g}, {@code path} (every command),
 * {@code polygon}, {@code polyline}, {@code rect} (with {@code rx}/{@code ry}), {@code circle},
 * {@code ellipse}, {@code image} with an embedded PNG ({@code data:image/png;base64}), and
 * {@code clipPath} referenced by {@code clip-path}; the {@code fill}, {@code fill-opacity},
 * {@code fill-rule}, {@code stroke}, {@code stroke-width}, {@code stroke-opacity},
 * {@code stroke-linecap}, {@code opacity}, {@code color}, {@code clip-rule}, {@code display} and
 * {@code visibility} properties as attributes or in {@code style}; every transform function.
 * Joins of strokes are always round. Text, gradients, masks, filters, CSS style sheets and
 * {@code use} are not rendered.</p>
 *
 * <p>Animations support {@code from}/{@code to}/{@code by}/{@code values}, {@code keyTimes},
 * {@code calcMode} linear, discrete and spline with {@code keySplines}, {@code begin} offsets,
 * {@code dur}, {@code repeatCount}, {@code fill="freeze"} and {@code additive="sum"}. Any numeric
 * attribute can be animated, so can {@code transform} with {@code animateTransform}.</p>
 */
final class SvgImage {

    private static final double DEFAULT_SIZE = 150;

    private final Element root;
    private final Map<String, Element> ids = new HashMap<>();
    private final double[] viewBox;
    private final double width;
    private final double height;
    private final double duration;
    private final boolean loops;

    private SvgImage(Element root) {
        this.root = root;
        index(root);
        double[] box = numbers(root.attr("viewBox"));
        double w = length(root.attr("width"), -1);
        double h = length(root.attr("height"), -1);
        if (box.length == 4 && box[2] > 0 && box[3] > 0) {
            viewBox = box;
            if (w <= 0 && h <= 0) {
                w = box[2];
                h = box[3];
            } else if (w <= 0) {
                w = h * box[2] / box[3];
            } else if (h <= 0) {
                h = w * box[3] / box[2];
            }
        } else {
            w = w > 0 ? w : DEFAULT_SIZE;
            h = h > 0 ? h : DEFAULT_SIZE;
            viewBox = new double[] {0, 0, w, h};
        }
        width = w;
        height = h;
        double end = 0;
        boolean indefinite = false;
        for (Animation animation : root.allAnimations()) {
            end = Math.max(end, animation.begin + animation.dur * (Double.isInfinite(animation.repeat) ? 1 : animation.repeat));
            indefinite |= Double.isInfinite(animation.repeat);
        }
        duration = end;
        loops = indefinite;
    }

    private void index(Element element) {
        String id = element.attr("id");
        if (id != null) {
            ids.putIfAbsent(id, element);
        }
        for (Element child : element.children) {
            index(child);
        }
    }

    /**
     * Parses an SVG document.
     *
     * @param svg The document
     * @return The image
     * @throws IllegalArgumentException If the document is not an SVG document
     */
    static SvgImage parse(String svg) {
        Element root = new XmlParser(svg).parseDocument();
        if (!"svg".equals(root.name)) {
            throw new IllegalArgumentException("Not an SVG document: <" + root.name + ">");
        }
        return new SvgImage(root);
    }

    /**
     * @return The root element, for tests
     */
    Element rootForTest() {
        return root;
    }

    /**
     * @return The width in CSS pixels
     */
    double width() {
        return width;
    }

    /**
     * @return The height in CSS pixels
     */
    double height() {
        return height;
    }

    /**
     * @return The time in seconds when every animation has ended, the length of one loop of the
     * indefinite animations, or 0 when nothing is animated
     */
    double duration() {
        return duration;
    }

    /**
     * @return Whether an animation repeats indefinitely, so the image should loop
     */
    boolean loops() {
        return loops;
    }

    /**
     * Renders the image as it is at a point in time, scaled to fill the raster.
     *
     * @param time The time in seconds
     * @param raster The raster to draw on
     * @param currentColor The ARGB value of {@code currentColor}
     */
    void render(double time, SvgRaster raster, int currentColor) {
        double[] matrix = viewport(viewBox, 0, 0, raster.width(), raster.height());
        Style style = new Style(0xFF000000, 1, false, 0, 1, 1, Stroker.BUTT, currentColor);
        render(root, time, matrix, style, 1, null, raster);
    }

    /**
     * @return The transform of a viewport that fits a view box with {@code xMidYMid meet}
     */
    private static double[] viewport(double[] box, double x, double y, double width, double height) {
        double scale = Math.min(width / box[2], height / box[3]);
        double tx = x + (width - box[2] * scale) / 2 - box[0] * scale;
        double ty = y + (height - box[3] * scale) / 2 - box[1] * scale;
        return new double[] {scale, 0, 0, scale, tx, ty};
    }

    private void render(Element element, double time, double[] parentMatrix, Style parentStyle, double parentOpacity,
                        float @Nullable [] inheritedMask, SvgRaster raster) {
        float @Nullable [] parentMask = inheritedMask;
        if ("none".equals(element.property("display", time))) {
            return;
        }
        double[] matrix = Affine.multiply(parentMatrix, element.transform(time));
        Style style = parentStyle.derive(element, time);
        double opacity = parentOpacity * clamp(element.number("opacity", time, 1));
        if (opacity <= 0) {
            return;
        }
        if ("svg".equals(element.name) && element != root) {
            double[] box = numbers(element.attr("viewBox"));
            double w = element.number("width", time, box.length == 4 ? box[2] : 0);
            double h = element.number("height", time, box.length == 4 ? box[3] : 0);
            if (w <= 0 || h <= 0) {
                return;
            }
            double x = element.number("x", time, 0);
            double y = element.number("y", time, 0);
            if (!"visible".equals(element.property("overflow", time))) {
                // a nested viewport clips its content to its bounds
                List<double[]> bounds = Geometry.of(new Element("polygon", Map.of("points",
                    x + "," + y + " " + (x + w) + "," + y + " " + (x + w) + "," + (y + h) + " " + x + "," + (y + h))), time).flatten(matrix);
                float[] viewportMask = new float[raster.width() * raster.height()];
                raster.coverage(bounds, false, viewportMask);
                if (parentMask != null) {
                    for (int i = 0; i < viewportMask.length; i++) {
                        viewportMask[i] *= parentMask[i];
                    }
                }
                parentMask = viewportMask;
            }
            matrix = Affine.multiply(matrix, viewport(box.length == 4 && box[2] > 0 && box[3] > 0 ? box : new double[] {0, 0, w, h}, x, y, w, h));
        }
        float[] mask = clip(element, time, matrix, parentMask, raster);
        switch (element.name) {
            case "svg", "g", "a" -> {
                for (Element child : element.children) {
                    render(child, time, matrix, style, opacity, mask, raster);
                }
            }
            case "path", "polygon", "polyline", "rect", "circle", "ellipse", "line" -> {
                if (!visible(element, time)) {
                    return;
                }
                Geometry geometry = Geometry.of(element, time);
                if (style.fill != 0 && !"line".equals(element.name)) {
                    double alpha = ((style.fill >>> 24) / 255.0) * style.fillOpacity * opacity;
                    raster.fill(geometry.flatten(matrix), style.evenOdd, style.fill, alpha, mask);
                }
                if (style.stroke != 0 && style.strokeWidth > 0) {
                    double scale = Math.sqrt(Math.abs(matrix[0] * matrix[3] - matrix[1] * matrix[2]));
                    double alpha = ((style.stroke >>> 24) / 255.0) * style.strokeOpacity * opacity;
                    raster.fill(geometry.stroke(matrix, style.strokeWidth * scale, style.lineCap), false, style.stroke, alpha, mask);
                }
            }
            case "image" -> {
                if (visible(element, time)) {
                    drawImage(element, time, matrix, opacity, mask, raster);
                }
            }
            default -> {
                // defs, clipPath, text, and everything else this renderer does not draw
            }
        }
    }

    private static boolean visible(Element element, double time) {
        String visibility = element.property("visibility", time);
        return !"hidden".equals(visibility) && !"collapse".equals(visibility);
    }

    private float @Nullable [] clip(Element element, double time, double[] matrix, float @Nullable [] parentMask, SvgRaster raster) {
        String clipPath = element.property("clip-path", time);
        if (clipPath == null || !clipPath.startsWith("url(")) {
            return parentMask;
        }
        String id = clipPath.substring(4, clipPath.indexOf(')') < 0 ? clipPath.length() : clipPath.indexOf(')'))
            .replace("\"", "").replace("'", "").trim();
        Element clip = ids.get(id.startsWith("#") ? id.substring(1) : id);
        if (clip == null || !"clipPath".equals(clip.name)) {
            return parentMask;
        }
        double[] clipMatrix = Affine.multiply(matrix, clip.transform(time));
        float[] mask = new float[raster.width() * raster.height()];
        for (Element child : clip.children) {
            if (Geometry.SHAPES.contains(child.name) && visible(child, time)) {
                String rule = child.property("clip-rule", time);
                if (rule == null) {
                    rule = clip.property("clip-rule", time);
                }
                List<double[]> polygons = Geometry.of(child, time).flatten(Affine.multiply(clipMatrix, child.transform(time)));
                raster.coverage(polygons, "evenodd".equals(rule), mask);
            }
        }
        if (parentMask != null) {
            for (int i = 0; i < mask.length; i++) {
                mask[i] *= parentMask[i];
            }
        }
        return mask;
    }

    private static void drawImage(Element element, double time, double[] matrix, double opacity, float @Nullable [] mask, SvgRaster raster) {
        String href = element.attr("href");
        if (href == null) {
            href = element.attr("xlink:href");
        }
        PngImage image = element.image(href);
        if (image == null) {
            return;
        }
        double w = element.number("width", time, image.width());
        double h = element.number("height", time, image.height());
        if (w <= 0 || h <= 0) {
            return;
        }
        double[] fit = viewport(new double[] {0, 0, image.width(), image.height()},
            element.number("x", time, 0), element.number("y", time, 0), w, h);
        raster.drawImage(image, Affine.multiply(matrix, fit), opacity, mask);
    }

    static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    static double[] numbers(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return new double[0];
        }
        NumberScanner scanner = new NumberScanner(value);
        List<Double> numbers = new ArrayList<>();
        while (scanner.hasNumber()) {
            numbers.add(scanner.next());
        }
        double[] result = new double[numbers.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = numbers.get(i);
        }
        return result;
    }

    static double length(@Nullable String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("px")) {
            trimmed = trimmed.substring(0, trimmed.length() - 2);
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a clock value such as {@code 1.5s}, {@code 200ms} or {@code 0.5}.
     *
     * @param value The value
     * @param fallback The value when absent or not a clock value
     * @return The seconds
     */
    static double seconds(@Nullable String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.endsWith("ms")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 2)) / 1000;
            }
            if (trimmed.endsWith("s")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1));
            }
            if (trimmed.endsWith("min")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 3)) * 60;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * The inherited painting properties.
     *
     * @param fill The ARGB fill, 0 for none
     * @param fillOpacity The fill opacity
     * @param evenOdd Whether the fill rule is even-odd
     * @param stroke The ARGB stroke, 0 for none
     * @param strokeOpacity The stroke opacity
     * @param strokeWidth The stroke width
     * @param lineCap The line cap of strokes
     * @param color The ARGB value of {@code currentColor}
     */
    private record Style(int fill, double fillOpacity, boolean evenOdd, int stroke, double strokeOpacity, double strokeWidth,
                         int lineCap, int color) {

        Style derive(Element element, double time) {
            int newColor = color;
            String colorValue = element.property("color", time);
            if (colorValue != null && !"inherit".equals(colorValue)) {
                Integer parsed = Paint.parse(colorValue, color);
                if (parsed != null) {
                    newColor = parsed;
                }
            }
            int newFill = paint(element.property("fill", time), fill, newColor);
            int newStroke = paint(element.property("stroke", time), stroke, newColor);
            double newFillOpacity = clamp(element.number("fill-opacity", time, fillOpacity));
            double newStrokeOpacity = clamp(element.number("stroke-opacity", time, strokeOpacity));
            double newStrokeWidth = Math.max(0, element.number("stroke-width", time, strokeWidth));
            String rule = element.property("fill-rule", time);
            boolean newEvenOdd = rule == null || "inherit".equals(rule) ? evenOdd : "evenodd".equals(rule);
            String cap = element.property("stroke-linecap", time);
            int newCap = cap == null || "inherit".equals(cap) ? lineCap : switch (cap.trim()) {
                case "round" -> Stroker.ROUND;
                case "square" -> Stroker.SQUARE;
                default -> Stroker.BUTT;
            };
            return new Style(newFill, newFillOpacity, newEvenOdd, newStroke, newStrokeOpacity, newStrokeWidth, newCap, newColor);
        }

        private int paint(@Nullable String value, int inherited, int newColor) {
            if (value != null && !"inherit".equals(value)) {
                Integer parsed = Paint.parse(value, newColor);
                return parsed == null ? 0 : parsed;
            }
            if (inherited != 0 && inherited == color) {
                // an inherited currentColor follows the color of this element
                return newColor;
            }
            return inherited;
        }
    }

    /**
     * An element with its attributes, the properties of its {@code style} attribute and the
     * animations that target it.
     */
    static final class Element {
        final String name;
        final Map<String, String> attributes;
        final List<Element> children = new ArrayList<>();
        final List<Animation> animations = new ArrayList<>();
        private @Nullable Map<String, String> style;
        private @Nullable Geometry geometry;
        private @Nullable String geometryKey;
        private @Nullable String imageKey;
        private @Nullable PngImage image;

        Element(String name, Map<String, String> attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        @Nullable String attr(String attribute) {
            return attributes.get(attribute);
        }

        /**
         * @param property The property
         * @param time The time
         * @return The value of a presentation property, from {@code style} first, with a {@code set}-like discrete animation applied
         */
        @Nullable String property(String property, double time) {
            String value = staticProperty(property);
            for (Animation animation : animations) {
                if (!animation.transform && animation.attribute.equals(property) && animation.texts != null) {
                    String animated = animation.text(time);
                    if (animated != null) {
                        value = animated;
                    }
                }
            }
            return value;
        }

        private @Nullable String staticProperty(String property) {
            if (style == null) {
                style = new HashMap<>();
                String declarations = attributes.get("style");
                if (declarations != null) {
                    for (String declaration : declarations.split(";")) {
                        int colon = declaration.indexOf(':');
                        if (colon > 0) {
                            style.put(declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT), declaration.substring(colon + 1).trim());
                        }
                    }
                }
            }
            String value = style.get(property);
            return value != null ? value : attributes.get(property);
        }

        /**
         * @param attribute The attribute
         * @param time The time
         * @param fallback The value when the attribute is absent
         * @return The numeric value of an attribute or property with its animations applied
         */
        double number(String attribute, double time, double fallback) {
            String base = staticProperty(attribute);
            double value = fallback;
            if (base != null) {
                double[] parsed = numbers(base.endsWith("%") ? base.substring(0, base.length() - 1) : base);
                if (parsed.length > 0) {
                    value = base.endsWith("%") ? parsed[0] / 100 : parsed[0];
                }
            }
            for (Animation animation : animations) {
                if (!animation.transform && animation.attribute.equals(attribute) && animation.texts == null) {
                    double[] animated = animation.value(time);
                    if (animated != null && animated.length > 0) {
                        value = animation.additive ? value + animated[0] : animated[0];
                    }
                }
            }
            return value;
        }

        /**
         * @param time The time
         * @return The transform of this element with its {@code animateTransform} animations applied
         */
        double[] transform(double time) {
            double[] matrix = Affine.parse(attributes.get("transform"));
            for (Animation animation : animations) {
                if (animation.transform) {
                    double[] values = animation.value(time);
                    if (values != null) {
                        double[] animated = Affine.of(animation.attribute, values);
                        matrix = animation.additive ? Affine.multiply(matrix, animated) : animated;
                    }
                }
            }
            return matrix;
        }

        Geometry geometry(String key, Supplier<Geometry> factory) {
            if (geometry == null || !key.equals(geometryKey)) {
                geometry = factory.get();
                geometryKey = key;
            }
            return geometry;
        }

        /**
         * @param href The reference of an image element
         * @return The decoded image, null if it is not an embedded PNG or cannot be decoded
         */
        @Nullable PngImage image(@Nullable String href) {
            if (href == null) {
                return null;
            }
            if (!href.equals(imageKey)) {
                imageKey = href;
                image = null;
                int comma = href.indexOf(',');
                String header = comma < 0 ? "" : href.substring(0, comma).replace(" ", "");
                if (header.startsWith("data:image/png") && header.endsWith(";base64")) {
                    try {
                        image = PngImage.decode(Base64.getMimeDecoder().decode(href.substring(comma + 1)));
                    } catch (IllegalArgumentException e) {
                        image = null;
                    }
                }
            }
            return image;
        }

        List<Animation> allAnimations() {
            List<Animation> all = new ArrayList<>(animations);
            for (Element child : children) {
                all.addAll(child.allAnimations());
            }
            return all;
        }
    }

    /**
     * A SMIL animation of one attribute.
     */
    static final class Animation {
        final String attribute;
        final boolean transform;
        final double begin;
        final double dur;
        final double repeat;
        final boolean freeze;
        final boolean additive;
        final boolean discrete;
        final double @Nullable [][] values;
        final String @Nullable [] texts;
        final double[] keyTimes;
        final double @Nullable [][] keySplines;

        Animation(Element element) {
            transform = "animateTransform".equals(element.name);
            String type = element.attr("type");
            attribute = transform ? (type == null ? "translate" : type.trim()) : String.valueOf(element.attr("attributeName")).trim();
            begin = seconds(element.attr("begin"), 0);
            dur = seconds(element.attr("dur"), 0);
            String repeatCount = element.attr("repeatCount");
            if (repeatCount == null) {
                repeat = 1;
            } else if ("indefinite".equals(repeatCount.trim())) {
                repeat = Double.POSITIVE_INFINITY;
            } else {
                repeat = Math.max(length(repeatCount, 1), 0);
            }
            freeze = "freeze".equals(element.attr("fill"));
            additive = "sum".equals(element.attr("additive"));
            String calcMode = element.attr("calcMode");
            String[] raw = rawValues(element);
            boolean numeric = true;
            for (String value : raw) {
                numeric &= numbers(value).length > 0;
            }
            if (numeric) {
                double[][] parsed = new double[raw.length][];
                for (int i = 0; i < raw.length; i++) {
                    parsed[i] = numbers(raw[i]);
                }
                values = parsed;
                texts = null;
                discrete = "discrete".equals(calcMode);
            } else {
                values = null;
                texts = raw;
                discrete = true;
            }
            int count = raw.length;
            double[] times = numbers(String.valueOf(element.attributes.getOrDefault("keyTimes", "")).replace(';', ' '));
            if (times.length != count) {
                // a discrete animation shows each value for an equal part of the duration
                times = new double[count];
                int intervals = discrete ? count : count - 1;
                for (int i = 0; i < count; i++) {
                    times[i] = intervals == 0 ? 0 : (double) i / intervals;
                }
            }
            keyTimes = times;
            if ("spline".equals(calcMode) && element.attr("keySplines") != null && count > 1) {
                String[] splines = element.attr("keySplines").split(";");
                double[][] parsed = new double[count - 1][];
                for (int i = 0; i < parsed.length; i++) {
                    double[] spline = i < splines.length ? numbers(splines[i]) : new double[0];
                    parsed[i] = spline.length == 4 ? spline : new double[] {0, 0, 1, 1};
                }
                keySplines = parsed;
            } else {
                keySplines = null;
            }
        }

        private String[] rawValues(Element element) {
            String valuesAttribute = element.attr("values");
            if (valuesAttribute != null && !valuesAttribute.isBlank()) {
                String[] split = valuesAttribute.split(";");
                List<String> nonEmpty = new ArrayList<>();
                for (String value : split) {
                    if (!value.isBlank()) {
                        nonEmpty.add(value.trim());
                    }
                }
                return nonEmpty.toArray(new String[0]);
            }
            String from = element.attr("from");
            String to = element.attr("to");
            String by = element.attr("by");
            if (to == null && by != null) {
                double[] start = numbers(from == null ? "0" : from);
                double[] delta = numbers(by);
                StringBuilder end = new StringBuilder();
                for (int i = 0; i < delta.length; i++) {
                    end.append(i < start.length ? start[i] + delta[i] : delta[i]).append(' ');
                }
                to = end.toString();
            }
            if (to == null) {
                return new String[0];
            }
            return from == null ? new String[] {to} : new String[] {from, to};
        }

        /**
         * @param time The document time
         * @return The progress through the current iteration in [0, 1], or -1 when the animation does not apply
         */
        private double progress(double time) {
            if (dur <= 0 || time < begin || keyTimes.length == 0) {
                return -1;
            }
            double local = time - begin;
            double active = dur * repeat;
            if (local >= active) {
                if (!freeze) {
                    return -1;
                }
                double fraction = repeat - Math.floor(repeat);
                return fraction == 0 ? 1 : fraction;
            }
            return (local % dur) / dur;
        }

        double @Nullable [] value(double time) {
            double progress = progress(time);
            if (progress < 0 || values == null) {
                return null;
            }
            if (values.length == 1) {
                return values[0];
            }
            int segment = segment(progress);
            if (discrete) {
                return values[segment];
            }
            if (segment >= values.length - 1) {
                return values[values.length - 1];
            }
            double span = keyTimes[segment + 1] - keyTimes[segment];
            double fraction = span <= 0 ? 1 : (progress - keyTimes[segment]) / span;
            if (keySplines != null) {
                fraction = Easing.cubicBezier(keySplines[segment], fraction);
            }
            double[] from = values[segment];
            double[] to = values[segment + 1];
            double[] result = new double[Math.max(from.length, to.length)];
            for (int i = 0; i < result.length; i++) {
                double a = i < from.length ? from[i] : 0;
                double b = i < to.length ? to[i] : 0;
                result[i] = a + (b - a) * fraction;
            }
            return result;
        }

        @Nullable String text(double time) {
            double progress = progress(time);
            if (progress < 0 || texts == null || texts.length == 0) {
                return null;
            }
            return texts[segment(progress)];
        }

        private int segment(double progress) {
            int segment = 0;
            for (int i = 0; i < keyTimes.length; i++) {
                if (progress >= keyTimes[i]) {
                    segment = i;
                }
            }
            return segment;
        }
    }

    /**
     * The cubic-bezier timing functions of {@code keySplines}.
     */
    static final class Easing {

        private Easing() {
        }

        static double cubicBezier(double[] spline, double x) {
            if (x <= 0 || x >= 1) {
                return x;
            }
            double low = 0;
            double high = 1;
            double t = x;
            for (int i = 0; i < 30; i++) {
                double estimate = bezier(spline[0], spline[2], t);
                if (Math.abs(estimate - x) < 1e-6) {
                    break;
                }
                if (estimate < x) {
                    low = t;
                } else {
                    high = t;
                }
                t = (low + high) / 2;
            }
            return bezier(spline[1], spline[3], t);
        }

        private static double bezier(double p1, double p2, double t) {
            double u = 1 - t;
            return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t;
        }
    }

    /**
     * Colors, as ARGB values.
     */
    static final class Paint {

        private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("black", 0xFF000000),
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("red", 0xFFFF0000),
            Map.entry("green", 0xFF008000),
            Map.entry("lime", 0xFF00FF00),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("orange", 0xFFFFA500),
            Map.entry("purple", 0xFF800080),
            Map.entry("gray", 0xFF808080),
            Map.entry("grey", 0xFF808080),
            Map.entry("silver", 0xFFC0C0C0),
            Map.entry("navy", 0xFF000080),
            Map.entry("teal", 0xFF008080),
            Map.entry("cyan", 0xFF00FFFF),
            Map.entry("magenta", 0xFFFF00FF)
        );

        private Paint() {
        }

        /**
         * @param value The paint
         * @param currentColor The value of {@code currentColor}
         * @return The ARGB color, null for none or a paint this renderer does not support
         */
        static @Nullable Integer parse(String value, int currentColor) {
            String paint = value.trim().toLowerCase(Locale.ROOT);
            if ("currentcolor".equals(paint)) {
                return currentColor;
            }
            if (paint.startsWith("#")) {
                String hex = paint.substring(1);
                try {
                    if (hex.length() == 3) {
                        int rgb = Integer.parseInt(hex, 16);
                        int r = (rgb >> 8) & 0xF;
                        int g = (rgb >> 4) & 0xF;
                        int b = rgb & 0xF;
                        return 0xFF000000 | (r * 17) << 16 | (g * 17) << 8 | b * 17;
                    }
                    if (hex.length() == 6) {
                        return 0xFF000000 | Integer.parseInt(hex, 16);
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
                return null;
            }
            if (paint.startsWith("rgb")) {
                double[] channels = numbers(paint.substring(paint.indexOf('(') + 1).replace(")", ""));
                if (channels.length >= 3) {
                    int alpha = channels.length > 3 ? (int) Math.round(clamp(channels[3]) * 255) : 255;
                    return alpha << 24 | channel(channels[0]) << 16 | channel(channels[1]) << 8 | channel(channels[2]);
                }
                return null;
            }
            if ("transparent".equals(paint)) {
                return 0;
            }
            return NAMED.get(paint);
        }

        private static int channel(double value) {
            return (int) Math.max(0, Math.min(255, Math.round(value)));
        }
    }

    /**
     * 2D affine matrices as {@code [a, b, c, d, e, f]}.
     */
    static final class Affine {

        static final double[] IDENTITY = {1, 0, 0, 1, 0, 0};

        private Affine() {
        }

        static double[] multiply(double[] m, double[] n) {
            return new double[] {
                m[0] * n[0] + m[2] * n[1],
                m[1] * n[0] + m[3] * n[1],
                m[0] * n[2] + m[2] * n[3],
                m[1] * n[2] + m[3] * n[3],
                m[0] * n[4] + m[2] * n[5] + m[4],
                m[1] * n[4] + m[3] * n[5] + m[5]
            };
        }

        static double[] parse(@Nullable String transform) {
            double[] matrix = IDENTITY;
            if (transform == null) {
                return matrix;
            }
            int index = 0;
            while (index < transform.length()) {
                int open = transform.indexOf('(', index);
                int close = transform.indexOf(')', open + 1);
                if (open < 0 || close < 0) {
                    break;
                }
                String function = transform.substring(index, open).replace(",", " ").trim();
                matrix = multiply(matrix, of(function, numbers(transform.substring(open + 1, close))));
                index = close + 1;
            }
            return matrix;
        }

        static double[] of(String function, double[] v) {
            return switch (function) {
                case "matrix" -> v.length == 6 ? v.clone() : IDENTITY;
                case "translate" -> v.length == 0 ? IDENTITY : new double[] {1, 0, 0, 1, v[0], v.length > 1 ? v[1] : 0};
                case "scale" -> v.length == 0 ? IDENTITY : new double[] {v[0], 0, 0, v.length > 1 ? v[1] : v[0], 0, 0};
                case "rotate" -> {
                    if (v.length == 0) {
                        yield IDENTITY;
                    }
                    double radians = Math.toRadians(v[0]);
                    double cos = Math.cos(radians);
                    double sin = Math.sin(radians);
                    double[] rotation = {cos, sin, -sin, cos, 0, 0};
                    if (v.length >= 3) {
                        rotation = multiply(multiply(new double[] {1, 0, 0, 1, v[1], v[2]}, rotation), new double[] {1, 0, 0, 1, -v[1], -v[2]});
                    }
                    yield rotation;
                }
                case "skewX" -> v.length == 0 ? IDENTITY : new double[] {1, 0, Math.tan(Math.toRadians(v[0])), 1, 0, 0};
                case "skewY" -> v.length == 0 ? IDENTITY : new double[] {1, Math.tan(Math.toRadians(v[0])), 0, 1, 0, 0};
                default -> IDENTITY;
            };
        }
    }

    /**
     * Reads the numbers of an attribute, separated by whitespace, commas, or nothing at all when a sign or
     * second decimal point starts the next one, as path data allows.
     */
    static final class NumberScanner {
        private final String text;
        private int position;

        NumberScanner(String text) {
            this.text = text;
        }

        private void skipSeparators() {
            while (position < text.length()) {
                char c = text.charAt(position);
                if (c == ',' || Character.isWhitespace(c)) {
                    position++;
                } else {
                    break;
                }
            }
        }

        boolean hasNumber() {
            skipSeparators();
            if (position >= text.length()) {
                return false;
            }
            char c = text.charAt(position);
            return Character.isDigit(c) || c == '-' || c == '+' || c == '.';
        }

        double next() {
            skipSeparators();
            int start = position;
            if (position < text.length() && (text.charAt(position) == '-' || text.charAt(position) == '+')) {
                position++;
            }
            boolean dot = false;
            boolean exponent = false;
            while (position < text.length()) {
                char c = text.charAt(position);
                if (Character.isDigit(c)) {
                    position++;
                } else if (c == '.' && !dot && !exponent) {
                    dot = true;
                    position++;
                } else if ((c == 'e' || c == 'E') && !exponent && position + 1 < text.length()
                    && (Character.isDigit(text.charAt(position + 1)) || text.charAt(position + 1) == '-' || text.charAt(position + 1) == '+')) {
                    exponent = true;
                    position += 2;
                } else {
                    break;
                }
            }
            if (start == position) {
                throw new IllegalArgumentException("Expected a number at " + start + " in: " + text);
            }
            return Double.parseDouble(text.substring(start, position));
        }

        /**
         * @return A flag of an arc command, which may be written without a separator
         */
        boolean nextFlag() {
            skipSeparators();
            if (position >= text.length()) {
                throw new IllegalArgumentException("Expected a flag in: " + text);
            }
            char c = text.charAt(position++);
            return c == '1';
        }

        @Nullable Character nextCommand() {
            skipSeparators();
            if (position < text.length() && Character.isLetter(text.charAt(position))) {
                return text.charAt(position++);
            }
            return null;
        }

        boolean atEnd() {
            skipSeparators();
            return position >= text.length();
        }
    }

    /**
     * A minimal XML reader: elements and attributes, skipping text, comments, processing instructions,
     * CDATA and the document type, which is never resolved.
     */
    static final class XmlParser {
        private final String xml;
        private int position;

        XmlParser(String xml) {
            this.xml = xml;
        }

        Element parseDocument() {
            List<Element> stack = new ArrayList<>();
            Element root = null;
            while (true) {
                int open = xml.indexOf('<', position);
                if (open < 0) {
                    break;
                }
                position = open;
                if (xml.startsWith("<!--", position)) {
                    position = end("-->");
                } else if (xml.startsWith("<![CDATA[", position)) {
                    position = end("]]>");
                } else if (xml.startsWith("<?", position)) {
                    position = end("?>");
                } else if (xml.startsWith("<!", position)) {
                    position = end(">");
                } else if (xml.startsWith("</", position)) {
                    position = end(">");
                    if (!stack.isEmpty()) {
                        stack.remove(stack.size() - 1);
                    }
                } else {
                    position++;
                    String name = localName(name());
                    Map<String, String> attributes = new HashMap<>();
                    boolean selfClosing = false;
                    while (true) {
                        skipWhitespace();
                        if (position >= xml.length()) {
                            throw new IllegalArgumentException("Unterminated element <" + name + ">");
                        }
                        char c = xml.charAt(position);
                        if (c == '/') {
                            selfClosing = true;
                            position = end(">");
                            break;
                        }
                        if (c == '>') {
                            position++;
                            break;
                        }
                        String attribute = localName(name());
                        skipWhitespace();
                        if (position < xml.length() && xml.charAt(position) == '=') {
                            position++;
                            skipWhitespace();
                            char quote = xml.charAt(position);
                            int close = xml.indexOf(quote, position + 1);
                            if ((quote != '"' && quote != '\'') || close < 0) {
                                throw new IllegalArgumentException("Malformed attribute " + attribute + " of <" + name + ">");
                            }
                            attributes.put(attribute, unescape(xml.substring(position + 1, close)));
                            position = close + 1;
                        }
                    }
                    Element element = new Element(name, attributes);
                    if (stack.isEmpty()) {
                        if (root == null) {
                            root = element;
                        }
                    } else {
                        Element parent = stack.get(stack.size() - 1);
                        if ("animate".equals(name) || "animateTransform".equals(name)) {
                            parent.animations.add(new Animation(element));
                        } else {
                            parent.children.add(element);
                        }
                    }
                    if (!selfClosing) {
                        stack.add(element);
                    }
                }
            }
            if (root == null) {
                throw new IllegalArgumentException("No root element");
            }
            return root;
        }

        private int end(String terminator) {
            int index = xml.indexOf(terminator, position);
            if (index < 0) {
                throw new IllegalArgumentException("Expected " + terminator);
            }
            return index + terminator.length();
        }

        private String name() {
            int start = position;
            while (position < xml.length()) {
                char c = xml.charAt(position);
                if (Character.isWhitespace(c) || c == '=' || c == '>' || c == '/') {
                    break;
                }
                position++;
            }
            if (start == position) {
                throw new IllegalArgumentException("Expected a name at " + start);
            }
            return xml.substring(start, position);
        }

        private static String localName(String name) {
            // xlink:href and the like keep their prefix, only the svg: element prefix is dropped
            return name.startsWith("svg:") ? name.substring(4) : name;
        }

        private void skipWhitespace() {
            while (position < xml.length() && Character.isWhitespace(xml.charAt(position))) {
                position++;
            }
        }

        private static String unescape(String value) {
            if (value.indexOf('&') < 0) {
                return value;
            }
            return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
        }
    }
}
