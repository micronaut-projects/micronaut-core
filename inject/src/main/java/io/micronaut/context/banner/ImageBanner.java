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

import io.micronaut.core.io.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Prints a banner from a PNG or SVG resource with the Kitty graphics protocol, which is implemented by
 * terminals such as Kitty, iTerm2 and WezTerm.
 *
 * <p>An SVG image may be animated with SMIL {@code <animate>} and {@code <animateTransform>}
 * elements: it is rendered to frames that are sent as a Kitty animation. The first frame sent is
 * the image at the end of its animation, so a terminal that displays images but not animations
 * still shows the complete banner. See {@link SvgImage} for the supported subset of SVG.</p>
 *
 * @since 5.3.0
 */
public class ImageBanner implements Banner {

    /**
     * The classpath location of the banner image.
     */
    public static final String RESOURCE = "META-INF/micronaut/banner.png";

    /**
     * The classpath location of the banner image for terminals with a light background, optional
     * since {@link #RESOURCE} is used for those without it.
     */
    public static final String LIGHT_RESOURCE = "META-INF/micronaut/banner-light.png";

    /**
     * The classpath location of the banner as an SVG image, which may be animated, used in preference to {@link #RESOURCE}.
     */
    public static final String SVG_RESOURCE = "META-INF/micronaut/banner.svg";

    /**
     * The classpath location of the SVG banner for terminals with a light background, optional
     * since {@link #SVG_RESOURCE} is used for those without it.
     */
    public static final String LIGHT_SVG_RESOURCE = "META-INF/micronaut/banner-light.svg";

    private static final Logger LOG = LoggerFactory.getLogger(ImageBanner.class);
    // The logo is drawn in currentColor, which is white on a dark background and black on a light one
    private static final String LOGO = "io/micronaut/context/banner/micronaut-banner/logo.svg";
    private static final int DARK_THEME_COLOR = 0xFFFFFFFF;
    private static final int LIGHT_THEME_COLOR = 0xFF000000;
    // Device pixels per terminal row an SVG scaled to rows is rendered with, enough for HiDPI displays
    private static final int PIXELS_PER_ROW = 40;
    private static final int FRAMES_PER_SECOND = 25;
    private static final double MAX_ANIMATION_SECONDS = 10;
    private static final int MAX_PIXELS = 4096 * 4096;
    // The logo is far larger than it is displayed so that it stays sharp on any display and font size
    private static final int LOGO_ROWS = 6;
    private static final String GRAPHICS_START = "\u001B_G";
    private static final String GRAPHICS_END = "\u001B\\";
    // errors are suppressed along with the OK responses, which the terminal would otherwise type into stdin
    private static final String QUIET = "q=2";
    private static final int CHUNK_SIZE = 4096;

    private final URL resource;
    private final PrintStream out;
    private final int rows;
    private final int currentColor;

    /**
     * Constructor for an image displayed at its pixel size. The terminal is not checked: use
     * {@link #resolve(ResourceLoader, PrintStream)} unless the output is known to display images.
     *
     * @param resource The PNG with the banner
     * @param out The print stream
     */
    public ImageBanner(URL resource, PrintStream out) {
        this(resource, out, 0, DARK_THEME_COLOR);
    }

    /**
     * Constructor.
     *
     * @param resource The PNG with the banner
     * @param out The print stream
     * @param rows The terminal rows to scale the image to with its aspect ratio kept, 0 for its pixel size
     * @param currentColor The ARGB value of {@code currentColor} in an SVG image
     */
    ImageBanner(URL resource, PrintStream out, int rows, int currentColor) {
        this.resource = resource;
        this.out = out;
        this.rows = rows;
        this.currentColor = currentColor;
    }

    /**
     * Finds the banner image of the application.
     *
     * @param resourceLoader The loader of the {@link #RESOURCE} and {@link #LIGHT_RESOURCE} images
     * @param out The print stream
     * @return The banner, empty if the terminal displays no images or the application has no image
     */
    public static Optional<Banner> resolve(ResourceLoader resourceLoader, PrintStream out) {
        return resolve(BannerTerminal.current(), resourceLoader, out);
    }

    static Optional<Banner> resolve(BannerTerminal terminal, ResourceLoader resourceLoader, PrintStream out) {
        int color = themeColor(terminal);
        return terminal.selectImage(resourceLoader::getResource, SVG_RESOURCE, LIGHT_SVG_RESOURCE)
            .or(() -> terminal.selectImage(resourceLoader::getResource, RESOURCE, LIGHT_RESOURCE))
            .map(image -> new ImageBanner(image, out, 0, color));
    }

    /**
     * Prints the Micronaut logo.
     *
     * @param terminal The terminal
     * @param out The print stream
     * @return Whether the logo was printed, false if the terminal displays no images or the logo cannot be read
     */
    static boolean printLogo(BannerTerminal terminal, PrintStream out) {
        ClassLoader classLoader = ImageBanner.class.getClassLoader();
        return terminal.selectImage(name -> Optional.ofNullable(classLoader.getResource(name)), LOGO, LOGO)
            .map(logo -> new ImageBanner(logo, out, LOGO_ROWS, themeColor(terminal)).printImage())
            .orElse(false);
    }

    private static int themeColor(BannerTerminal terminal) {
        return terminal.isLightTheme() ? LIGHT_THEME_COLOR : DARK_THEME_COLOR;
    }

    @Override
    public void print() {
        printImage();
    }

    /**
     * Prints the image, nothing at all if it cannot be read.
     *
     * @return Whether the image was printed
     */
    boolean printImage() {
        byte[] image;
        try (InputStream stream = resource.openStream()) {
            image = stream.readAllBytes();
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("There was an error printing the banner.", e);
            }
            return false;
        }
        if (!resource.getPath().endsWith(".svg")) {
            out.println();
            transmit("a=T,f=100," + QUIET + scaling(), image);
            out.println();
            return true;
        }
        SvgImage svg;
        try {
            svg = SvgImage.parse(new String(image, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("The banner {} is not a valid SVG image.", resource, e);
            }
            return false;
        }
        printSvg(svg);
        return true;
    }

    private String scaling() {
        return rows > 0 ? ",r=" + rows : "";
    }

    /**
     * Prints an SVG image, as an animation if it is animated. The image at the end of its animation
     * is the root frame, which is skipped while the animation plays.
     */
    private void printSvg(SvgImage svg) {
        int height = rows > 0 ? rows * PIXELS_PER_ROW : (int) Math.ceil(svg.height());
        int width = (int) Math.ceil(height * svg.width() / svg.height());
        if ((long) width * height > MAX_PIXELS) {
            double scale = Math.sqrt((double) MAX_PIXELS / ((long) width * height));
            width = (int) (width * scale);
            height = (int) (height * scale);
        }
        double duration = Math.min(svg.duration(), MAX_ANIMATION_SECONDS);
        byte[] last = render(svg, duration, width, height);
        out.println();
        if (duration <= 0) {
            transmit("a=T,f=100," + QUIET + scaling(), last);
            out.println();
            return;
        }
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        transmit("a=T,f=100,i=" + id + "," + QUIET + scaling(), last);
        int frames = (int) Math.ceil(duration * FRAMES_PER_SECOND);
        int frameMillis = 1000 / FRAMES_PER_SECOND;
        byte[] pending = render(svg, 0, width, height);
        int pendingGap = frameMillis;
        for (int frame = 1; frame <= frames; frame++) {
            byte[] png = render(svg, Math.min(duration, (double) frame / FRAMES_PER_SECOND), width, height);
            if (Arrays.equals(pending, png)) {
                // an unchanged frame lengthens the previous one rather than being sent again
                pendingGap += frameMillis;
                continue;
            }
            transmit("a=f,f=100,i=" + id + ",z=" + pendingGap + "," + QUIET, pending);
            pending = png;
            pendingGap = frameMillis;
        }
        transmit("a=f,f=100,i=" + id + ",z=" + pendingGap + "," + QUIET, pending);
        // the root frame is gapless so that it is skipped, then the animation plays once or loops
        control("a=a,i=" + id + ",r=1,z=-1," + QUIET);
        control("a=a,i=" + id + ",s=3,v=" + (svg.loops() ? 1 : 2) + "," + QUIET);
        out.println();
    }

    private byte[] render(SvgImage svg, double time, int width, int height) {
        SvgRaster raster = new SvgRaster(width, height);
        svg.render(time, raster, currentColor);
        return raster.toPng();
    }

    private void control(String control) {
        out.print(GRAPHICS_START + control + GRAPHICS_END);
    }

    /**
     * Sends data in chunks of at most {@value #CHUNK_SIZE} base64 bytes, as the protocol requires.
     */
    private void transmit(String firstControl, byte[] data) {
        String payload = Base64.getEncoder().encodeToString(data);
        String control = firstControl;
        for (int start = 0; start < payload.length(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, payload.length());
            out.print(GRAPHICS_START + control + ",m=" + (end < payload.length() ? 1 : 0) + ";");
            out.append(payload, start, end);
            out.print(GRAPHICS_END);
            control = QUIET;
        }
    }
}
