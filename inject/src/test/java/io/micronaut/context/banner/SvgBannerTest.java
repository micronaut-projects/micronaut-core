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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SvgBannerTest {

    private static final Pattern GRAPHICS = Pattern.compile("\u001B_G([^;\u001B]*)(?:;([^\u001B]*))?\u001B\\\\");
    private static final String LOGO = "io/micronaut/context/banner/micronaut-banner/logo.svg";
    private static final int RED = 0xFFFF0000;

    @Test
    void rectangleIsFilledWithAntiAliasedEdges() throws IOException {
        BufferedImage image = render("""
            <svg width="20" height="10" xmlns="http://www.w3.org/2000/svg">
              <rect x="2" y="2" width="10.5" height="6" fill="#ff0000"/>
            </svg>""", 0);

        assertEquals(0xFFFF0000, image.getRGB(5, 5));
        assertEquals(0, image.getRGB(1, 1) >>> 24);
        assertEquals(0, image.getRGB(15, 5) >>> 24);
        int halfCovered = image.getRGB(12, 5) >>> 24;
        assertTrue(halfCovered > 100 && halfCovered < 155, "alpha " + halfCovered);
    }

    @Test
    void fillRulesDecideWhetherAnInnerSubpathIsAHole() throws IOException {
        String shape = "M0 0 H20 V20 H0 Z M5 5 H15 V15 H5 Z";
        BufferedImage nonZero = render("<svg width='20' height='20'><path d='" + shape + "'/></svg>", 0);
        BufferedImage evenOdd = render("<svg width='20' height='20'><path fill-rule='evenodd' d='" + shape + "'/></svg>", 0);

        assertEquals(0xFF000000, nonZero.getRGB(10, 10));
        assertEquals(0, evenOdd.getRGB(10, 10) >>> 24);
        assertEquals(0xFF000000, evenOdd.getRGB(2, 2));
    }

    @Test
    void everyPathCommandIsParsed() {
        // relative and absolute commands, implicit repeats, arcs with flags written without separators
        String d = "m10,10 h20 v20 H10 Z M40 10 l10 0 0 10 -10 0z M60 10 C60 20 70 20 70 10 S80 0 80 10 "
            + "Q85 20 90 10 T100 10 M110 20 a10 10 0 1 0 20 0 A10 10 0 01110 20";
        Geometry geometry = Geometry.path(d);

        List<double[]> polygons = geometry.flatten(SvgImage.Affine.IDENTITY);

        assertEquals(4, polygons.size());
        double[] circle = polygons.get(3);
        for (int i = 0; i < circle.length; i += 2) {
            assertEquals(10, Math.hypot(circle[i] - 120, circle[i + 1] - 20), 0.05, "a point of the arc is off the circle");
        }
    }

    @Test
    void transformsGroupsAndCurrentColorAreApplied() throws IOException {
        BufferedImage image = render("""
            <svg viewBox="0 0 10 10" width="20" height="20" xmlns="http://www.w3.org/2000/svg">
              <g fill="currentColor" style="opacity: 0.5" transform="translate(5 0)">
                <rect width="5" height="5"/>
              </g>
              <g color="#00ff00"><circle cx="2.5" cy="7.5" r="2" fill="currentColor"/></g>
            </svg>""", 0, 0xFF0000FF);

        int pixel = image.getRGB(15, 5);
        assertEquals(0x0000FF, pixel & 0xFFFFFF);
        assertEquals(128, pixel >>> 24, 1);
        assertEquals(0, image.getRGB(5, 5) >>> 24, "translated away");
        assertEquals(0xFF00FF00, image.getRGB(5, 15));
    }

    @Test
    void animationsAreSampledOverTime() {
        SvgImage svg = SvgImage.parse("""
            <svg width="10" height="10">
              <rect width="10" height="10">
                <animate attributeName="opacity" from="0" to="1" begin="1s" dur="2s" fill="freeze"/>
                <animate attributeName="width" values="0;10;4" keyTimes="0;0.5;1" dur="1000ms"/>
                <animateTransform attributeName="transform" type="rotate" values="0 5 5;90 5 5" dur="1s" repeatCount="indefinite"/>
              </rect>
            </svg>""");
        SvgImage.Element rect = svg.rootForTest().children.get(0);

        assertEquals(3, svg.duration(), 1e-9);
        assertTrue(svg.loops());
        assertEquals(1, rect.number("opacity", 0.5, 1), 1e-9, "the base value before the animation begins");
        assertEquals(0.25, rect.number("opacity", 1.5, 1), 1e-9);
        assertEquals(1, rect.number("opacity", 5, 1), 1e-9, "frozen at the end");
        assertEquals(5, rect.number("width", 0.25, 0), 1e-9);
        assertEquals(7, rect.number("width", 0.75, 0), 1e-9);
        assertEquals(10, rect.number("width", 2, 0), 1e-9, "the base value after it ends");
        double[] rotated = rect.transform(1.5);
        assertEquals(Math.cos(Math.toRadians(45)), rotated[0], 1e-9, "the transform repeats every second");
    }

    @Test
    void splinesEaseTheInterpolation() {
        SvgImage svg = SvgImage.parse("""
            <svg width="10" height="10"><rect width="10" height="10">
              <animate attributeName="x" values="0;100" dur="1s" calcMode="spline" keySplines="0.42 0 1 1"/>
              <animate attributeName="fill" values="red;blue" dur="1s"/>
            </rect></svg>""");
        SvgImage.Element rect = svg.rootForTest().children.get(0);

        double eased = rect.number("x", 0.5, 0);
        assertTrue(eased > 25 && eased < 40, "ease-in lags behind linear: " + eased);
        assertEquals("red", rect.property("fill", 0.25));
        assertEquals("blue", rect.property("fill", 0.75));
    }

    @Test
    void staticSvgIsSentAsOneImage(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("banner.svg"), "<svg width='40' height='20'><rect width='40' height='20' fill='red'/></svg>");

        List<Chunk> chunks = chunks(capture(out -> new ImageBanner(file.toUri().toURL(), out).print()));

        assertEquals(1, chunks.size());
        assertEquals("a=T,f=100,q=2,m=0", chunks.get(0).control());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(decode(chunks)));
        assertEquals(40, image.getWidth());
        assertEquals(RED, image.getRGB(20, 10));
    }

    @Test
    void animatedSvgIsSentAsAnAnimationWhoseRootFrameIsTheEnd(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("banner.svg"), """
            <svg width="40" height="20">
              <rect width="40" height="20" fill="red">
                <animate attributeName="opacity" values="0;1" dur="0.2s" fill="freeze"/>
              </rect>
              <rect width="1" height="1" fill="none"><animate attributeName="x" values="0;0" begin="1s" dur="1s"/></rect>
            </svg>""");

        List<Chunk> chunks = chunks(capture(out -> new ImageBanner(file.toUri().toURL(), out).print()));

        String root = chunks.get(0).control();
        assertTrue(root.matches("a=T,f=100,i=\\d+,q=2,m=0"), root);
        String id = root.replaceAll(".*i=(\\d+).*", "$1");
        List<Chunk> frames = chunks.stream().filter(chunk -> chunk.control().startsWith("a=f")).toList();
        // 0, 0.04, 0.08, 0.12, 0.16 and 0.2 seconds differ, the last of them is shown until the 2 seconds end
        assertEquals(6, frames.size());
        assertEquals("a=f,f=100,i=" + id + ",z=40,q=2,m=0", frames.get(0).control());
        assertEquals("a=f,f=100,i=" + id + ",z=1840,q=2,m=0", frames.get(5).control());
        assertEquals("a=a,i=" + id + ",r=1,z=-1,q=2", chunks.get(chunks.size() - 2).control());
        assertEquals("a=a,i=" + id + ",s=3,v=2,q=2", chunks.get(chunks.size() - 1).control());

        BufferedImage end = ImageIO.read(new ByteArrayInputStream(decode(chunks.subList(0, 1))));
        BufferedImage start = ImageIO.read(new ByteArrayInputStream(decode(frames.subList(0, 1))));
        assertEquals(RED, end.getRGB(20, 10));
        assertEquals(0, start.getRGB(20, 10) >>> 24);
    }

    @Test
    void invalidSvgPrintsNothing(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("banner.svg"), "<html></html>");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertFalse(new ImageBanner(file.toUri().toURL(), new PrintStream(bytes)).printImage());
        assertEquals(0, bytes.size());
    }

    @Test
    void defaultLogoIsAnimatedAndEndsAsTheCompleteLogoInTheThemeColor() throws IOException {
        for (boolean light : new boolean[] {false, true}) {
            BannerTerminal terminal = new BannerTerminal(
                name -> switch (name) {
                    case "MICRONAUT_BANNER_MODE" -> "kitty";
                    case "MICRONAUT_BANNER_THEME" -> light ? "light" : "dark";
                    default -> null;
                },
                () -> false);
            List<Chunk> chunks = chunks(capture(out -> new MicronautBanner(out, terminal).print()));

            assertTrue(chunks.get(0).control().matches("a=T,f=100,i=\\d+,q=2,r=6,m=1"), chunks.get(0).control());
            assertTrue(chunks.stream().filter(chunk -> chunk.control().startsWith("a=f")).count() > 10);
            BufferedImage end = ImageIO.read(new ByteArrayInputStream(decode(rootFrame(chunks))));
            assertEquals(240, end.getHeight());
            int ink = 0;
            for (int y = 0; y < end.getHeight(); y++) {
                for (int x = 0; x < end.getWidth(); x++) {
                    int pixel = end.getRGB(x, y);
                    if (pixel >>> 24 == 0xFF) {
                        assertEquals(light ? 0 : 0xFFFFFF, pixel & 0xFFFFFF);
                        ink++;
                    }
                }
            }
            assertTrue(ink > end.getWidth() * end.getHeight() / 40, "the logo is drawn: " + ink);
        }
    }

    /**
     * Writes the frames of the default logo as PNG files to look at, run with
     * {@code SVG_BANNER_FRAMES=/some/dir}.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SVG_BANNER_FRAMES", matches = ".+")
    void writeLogoFrames() throws IOException {
        Path dir = Files.createDirectories(Path.of(System.getenv("SVG_BANNER_FRAMES")));
        String logo;
        try (InputStream stream = SvgBannerTest.class.getClassLoader().getResourceAsStream(LOGO)) {
            logo = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        SvgImage svg = SvgImage.parse(logo);
        long start = System.nanoTime();
        int frames = (int) Math.ceil(svg.duration() * 25);
        for (int frame = 0; frame <= frames; frame++) {
            SvgRaster raster = new SvgRaster(1059, 240);
            svg.render(frame / 25.0, raster, 0xFF202020);
            Files.write(dir.resolve("frame-%03d.png".formatted(frame)), raster.toPng());
        }
        System.out.println("Rendered " + (frames + 1) + " frames in " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static List<Chunk> rootFrame(List<Chunk> chunks) {
        List<Chunk> root = new ArrayList<>();
        for (Chunk chunk : chunks) {
            root.add(chunk);
            if (chunk.control().endsWith("m=0")) {
                return root;
            }
        }
        throw new AssertionError("Unterminated image");
    }

    private static BufferedImage render(String svg, double time) throws IOException {
        return render(svg, time, 0xFF000000);
    }

    private static BufferedImage render(String svg, double time, int currentColor) throws IOException {
        SvgImage image = SvgImage.parse(svg);
        SvgRaster raster = new SvgRaster((int) image.width(), (int) image.height());
        image.render(time, raster, currentColor);
        return ImageIO.read(new ByteArrayInputStream(raster.toPng()));
    }

    private static String capture(ThrowingBanner banner) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            banner.printTo(out);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static List<Chunk> chunks(String output) {
        List<Chunk> chunks = new ArrayList<>();
        Matcher matcher = GRAPHICS.matcher(output);
        while (matcher.find()) {
            chunks.add(new Chunk(matcher.group(1), matcher.group(2) == null ? "" : matcher.group(2)));
        }
        return chunks;
    }

    private static byte[] decode(List<Chunk> chunks) {
        StringBuilder payload = new StringBuilder();
        chunks.forEach(chunk -> payload.append(chunk.payload()));
        return Base64.getDecoder().decode(payload.toString());
    }

    private record Chunk(String control, String payload) {
    }

    @FunctionalInterface
    private interface ThrowingBanner {
        void printTo(PrintStream out) throws IOException;
    }
}
