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
import java.util.Base64;
import java.util.Optional;

/**
 * Prints a banner from a PNG resource with the Kitty graphics protocol, which is implemented by
 * terminals such as Kitty, iTerm2 and WezTerm.
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

    private static final Logger LOG = LoggerFactory.getLogger(ImageBanner.class);
    // The white logo, for the dark background terminals usually have
    private static final String LOGO = "io/micronaut/context/banner/micronaut-banner/logo.png";
    private static final String LOGO_LIGHT = "io/micronaut/context/banner/micronaut-banner/logo-light.png";
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

    /**
     * Constructor for an image displayed at its pixel size. The terminal is not checked: use
     * {@link #resolve(ResourceLoader, PrintStream, BannerMode)} unless the output is known to display images.
     *
     * @param resource The PNG with the banner
     * @param out The print stream
     */
    public ImageBanner(URL resource, PrintStream out) {
        this(resource, out, 0);
    }

    /**
     * Constructor.
     *
     * @param resource The PNG with the banner
     * @param out The print stream
     * @param rows The terminal rows to scale the image to with its aspect ratio kept, 0 for its pixel size
     */
    ImageBanner(URL resource, PrintStream out, int rows) {
        this.resource = resource;
        this.out = out;
        this.rows = rows;
    }

    /**
     * Finds the banner image of the application.
     *
     * @param resourceLoader The loader of the {@link #RESOURCE} and {@link #LIGHT_RESOURCE} images
     * @param out The print stream
     * @param mode The banner mode
     * @return The banner, empty if the terminal displays no images or the application has no image
     */
    public static Optional<Banner> resolve(ResourceLoader resourceLoader, PrintStream out, BannerMode mode) {
        return BannerTerminal.current(mode).selectImage(resourceLoader::getResource, RESOURCE, LIGHT_RESOURCE)
            .map(image -> new ImageBanner(image, out));
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
        return terminal.selectImage(name -> Optional.ofNullable(classLoader.getResource(name)), LOGO, LOGO_LIGHT)
            .map(logo -> new ImageBanner(logo, out, LOGO_ROWS).printImage())
            .orElse(false);
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
        String payload = Base64.getEncoder().encodeToString(image);
        String control = "a=T,f=100," + QUIET + (rows > 0 ? ",r=" + rows : "");
        out.println();
        for (int start = 0; start < payload.length(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, payload.length());
            out.print(GRAPHICS_START + control + ",m=" + (end < payload.length() ? 1 : 0) + ";");
            out.append(payload, start, end);
            out.print(GRAPHICS_END);
            control = QUIET;
        }
        out.println();
        return true;
    }
}
