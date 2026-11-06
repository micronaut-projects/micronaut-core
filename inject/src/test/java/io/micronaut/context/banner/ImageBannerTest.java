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

import com.github.stefanbirkner.systemlambda.SystemLambda;
import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageBannerTest {

    private static final Pattern GRAPHICS = Pattern.compile("\u001B_G([^;]*);([^\u001B]*)\u001B\\\\");
    private static final String ANIMATED_LOGO = "a=T,f=100,i=\\d+,q=2,r=6,m=1";

    @Test
    void largeImageIsSentInProtocolChunks(@TempDir Path dir) throws IOException {
        byte[] image = new byte[10_000];
        new Random(1).nextBytes(image);
        Path file = Files.write(dir.resolve("image.png"), image);

        List<Chunk> chunks = chunks(capture(out -> new ImageBanner(file.toUri().toURL(), out).print()));

        assertEquals(4, chunks.size());
        assertEquals("a=T,f=100,q=2,m=1", chunks.get(0).control());
        assertEquals("q=2,m=1", chunks.get(1).control());
        assertEquals("q=2,m=1", chunks.get(2).control());
        assertEquals("q=2,m=0", chunks.get(3).control());
        for (Chunk chunk : chunks.subList(0, 3)) {
            assertEquals(4096, chunk.payload().length());
        }
        assertArrayEquals(image, decode(chunks));
    }

    @Test
    void smallImageIsSentInOneChunk(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("image.png"), new byte[] {1, 2, 3});

        List<Chunk> chunks = chunks(capture(out -> new ImageBanner(file.toUri().toURL(), out).print()));

        assertEquals(1, chunks.size());
        assertEquals("a=T,f=100,q=2,m=0", chunks.get(0).control());
    }

    @Test
    void unreadableImagePrintsNothing(@TempDir Path dir) throws IOException {
        URL missing = dir.resolve("missing.png").toUri().toURL();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        assertFalse(new ImageBanner(missing, new PrintStream(bytes)).printImage());

        assertEquals(0, bytes.size());
    }

    @Test
    void imageSupportIsDetectedOnlyInAnInteractiveTerminal() {
        for (Map<String, String> env : List.of(
            Map.of("KITTY_WINDOW_ID", "1"),
            Map.of("TERM", "xterm-kitty"),
            Map.of("TERM_PROGRAM", "iTerm.app"),
            Map.of("TERM_PROGRAM", "WezTerm"),
            Map.of("TERM_PROGRAM", "WezTerm", "MICRONAUT_BANNER_MODE", "auto"))) {
            assertTrue(terminal(env, true).supportsImages(), env.toString());
            assertFalse(terminal(env, false).supportsImages(), env.toString());
        }
        assertFalse(terminal(Map.of("TERM", "xterm-256color", "TERM_PROGRAM", "Apple_Terminal"), true).supportsImages());
    }

    @Test
    void imageSupportCanBeForced() {
        assertTrue(terminal(Map.of("MICRONAUT_BANNER_MODE", "kitty"), false).supportsImages());
        assertFalse(terminal(Map.of("MICRONAUT_BANNER_MODE", "ascii", "TERM", "xterm-kitty"), true).supportsImages());
        assertFalse(terminal(Map.of("MICRONAUT_BANNER_MODE", "other", "TERM", "xterm-kitty"), true).supportsImages());
    }

    @Test
    void lightThemeIsDetectedFromTheBackgroundColor() {
        assertTrue(terminal(Map.of("COLORFGBG", "0;15"), true).isLightTheme());
        assertTrue(terminal(Map.of("COLORFGBG", "0;default;7"), true).isLightTheme());
        assertFalse(terminal(Map.of("COLORFGBG", "15;0"), true).isLightTheme());
        assertFalse(terminal(Map.of("COLORFGBG", "15"), true).isLightTheme());
        assertFalse(terminal(Map.of("COLORFGBG", "15;default"), true).isLightTheme());
        assertFalse(terminal(Map.of(), true).isLightTheme());
        assertTrue(terminal(Map.of("MICRONAUT_BANNER_THEME", "light", "COLORFGBG", "15;0"), true).isLightTheme());
        assertFalse(terminal(Map.of("MICRONAUT_BANNER_THEME", "dark", "COLORFGBG", "0;15"), true).isLightTheme());
    }

    @Test
    void customImageIsSelectedByTheme(@TempDir Path dir) throws IOException {
        URL image = dir.resolve("banner.png").toUri().toURL();
        URL lightImage = dir.resolve("banner-light.png").toUri().toURL();
        Map<String, URL> both = Map.of("dark", image, "light", lightImage);
        Map<String, URL> single = Map.of("dark", image);
        BannerTerminal dark = terminal(Map.of("MICRONAUT_BANNER_MODE", "kitty"), false);
        BannerTerminal light = terminal(Map.of("MICRONAUT_BANNER_MODE", "kitty", "MICRONAUT_BANNER_THEME", "light"), false);

        assertEquals(Optional.of(image), dark.selectImage(name -> Optional.ofNullable(both.get(name)), "dark", "light"));
        assertEquals(Optional.of(lightImage), light.selectImage(name -> Optional.ofNullable(both.get(name)), "dark", "light"));
        assertEquals(Optional.of(image), light.selectImage(name -> Optional.ofNullable(single.get(name)), "dark", "light"));
        assertEquals(Optional.empty(), dark.selectImage(name -> Optional.empty(), "dark", "light"));
        assertEquals(Optional.empty(), terminal(Map.of(), false).selectImage(name -> Optional.of(image), "dark", "light"));
    }

    @Test
    void defaultBannerPrintsAsciiWhenTheTerminalDisplaysNoImages() {
        String output = capture(out -> new MicronautBanner(out, terminal(Map.of(), false)).print());

        assertTrue(chunks(output).isEmpty());
        assertTrue(output.contains("|  \\/  (_) ___ _ __ ___"), output);
    }

    @Test
    void defaultBannerPrintsTheLogoScaledToRows() throws IOException {
        String dark = capture(out -> new MicronautBanner(out, terminal(Map.of("MICRONAUT_BANNER_MODE", "kitty"), false)).print());
        String light = capture(out -> new MicronautBanner(out, terminal(Map.of("MICRONAUT_BANNER_MODE", "kitty", "MICRONAUT_BANNER_THEME", "light"), false)).print());

        assertTrue(chunks(dark).get(0).control().matches(ANIMATED_LOGO), chunks(dark).get(0).control());
        assertEquals(0xFFFFFF, inkColor(rootFrame(chunks(dark))), "the dark theme logo is white");
        assertEquals(0x000000, inkColor(rootFrame(chunks(light))), "the light theme logo is black");
    }

    @Test
    void applicationBannerImageIsPreferredOverTheTextBanner(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("META-INF/micronaut"));
        byte[] image = {1, 2, 3, 4};
        Files.write(dir.resolve(ImageBanner.RESOURCE), image);
        Files.writeString(dir.resolve("micronaut-banner.txt"), "custom text banner");

        assertArrayEquals(image, decode(chunks(applicationOutput(dir, "kitty"))));

        String ascii = applicationOutput(dir, "ascii");
        assertTrue(chunks(ascii).isEmpty());
        assertTrue(ascii.contains("custom text banner"), ascii);
    }

    @Test
    void applicationSvgBannerIsPreferredOverItsPngBanner(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("META-INF/micronaut"));
        Files.write(dir.resolve(ImageBanner.RESOURCE), new byte[] {1, 2, 3, 4});
        Files.writeString(dir.resolve(ImageBanner.SVG_RESOURCE), "<svg width='8' height='4'><rect width='8' height='4'/></svg>");

        List<Chunk> chunks = chunks(applicationOutput(dir, "kitty"));

        assertEquals(1, chunks.size());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(decode(chunks)));
        assertEquals(8, image.getWidth());
        assertEquals(0xFF000000, image.getRGB(4, 2));
    }

    @Test
    void textBannerIsPreferredOverTheDefaultLogo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("micronaut-banner.txt"), "custom text banner");

        String output = applicationOutput(dir, "kitty");

        assertTrue(chunks(output).isEmpty());
        assertTrue(output.contains("custom text banner"), output);
    }

    @Test
    void defaultLogoIsPrintedWithoutCustomBanners(@TempDir Path dir) throws Exception {
        String control = chunks(applicationOutput(dir, "kitty")).get(0).control();

        assertTrue(control.matches(ANIMATED_LOGO), control);
    }

    private static String applicationOutput(Path dir, String mode) throws Exception {
        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {dir.toUri().toURL()}, ImageBannerTest.class.getClassLoader())) {
            String[] output = new String[1];
            SystemLambda.withEnvironmentVariable("MICRONAUT_BANNER_MODE", mode)
                .and("MICRONAUT_BANNER_THEME", "dark")
                .execute(() -> output[0] = SystemLambda.tapSystemOut(() -> {
                    try (ApplicationContext ignored = Micronaut.build(new String[0]).classLoader(classLoader).deduceEnvironment(false).start()) {
                        // only the banner printed by the start matters
                    }
                }));
            return output[0];
        }
    }

    private static BannerTerminal terminal(Map<String, String> env, boolean interactive) {
        return new BannerTerminal(env::get, () -> interactive);
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
            chunks.add(new Chunk(matcher.group(1), matcher.group(2)));
        }
        return chunks;
    }

    private static byte[] decode(List<Chunk> chunks) {
        StringBuilder payload = new StringBuilder();
        chunks.forEach(chunk -> payload.append(chunk.payload()));
        return Base64.getDecoder().decode(payload.toString());
    }

    private static byte[] rootFrame(List<Chunk> chunks) {
        List<Chunk> root = new ArrayList<>();
        for (Chunk chunk : chunks) {
            root.add(chunk);
            if (chunk.control().endsWith("m=0")) {
                return decode(root);
            }
        }
        throw new AssertionError("Unterminated image");
    }

    private static int inkColor(byte[] png) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                if (pixel >>> 24 == 0xFF) {
                    return pixel & 0xFFFFFF;
                }
            }
        }
        throw new AssertionError("The image has no opaque pixels");
    }

    private record Chunk(String control, String payload) {
    }

    @FunctionalInterface
    private interface ThrowingBanner {
        void printTo(PrintStream out) throws IOException;
    }
}
