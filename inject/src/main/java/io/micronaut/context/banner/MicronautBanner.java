/*
 * Copyright 2017-2020 original authors
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

import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Base64;
import java.util.Locale;

/**
 * Default implementation of {@link Banner} that prints the default Micronaut banner.
 *
 * It tries to emit a bitmap if a modern terminal that supports inline images is detected (e.g.
 * iTerm2, Kitty, WesTerm, Windows Terminal) although the quality of the output will depend on the
 * feature set.
 */
public class MicronautBanner implements Banner {
    private static final String CUSTOM_PNG_RESOURCE = "micronaut-banner.png";
    private static final String CUSTOM_LIGHT_PNG_RESOURCE = "micronaut-banner-light.png";
    private static final String BANNER_MODE_ENV = "MICRONAUT_BANNER_MODE";
    private static final String BANNER_THEME_ENV = "MICRONAUT_BANNER_THEME";
    private static final String[] MICRONAUT_BANNER = {
            " __  __ _                                  _   ",
            "|  \\/  (_) ___ _ __ ___  _ __   __ _ _   _| |_ ",
            "| |\\/| | |/ __| '__/ _ \\| '_ \\ / _` | | | | __|",
            "| |  | | | (__| | | (_) | | | | (_| | |_| | |_ ",
            "|_|  |_|_|\\___|_|  \\___/|_| |_|\\__,_|\\__,_|\\__|"
    };


    /**
     * Path to the bitmap for the dark mode version of the logo (i.e. white on black).
     * This is public and mutable so variants of Micronaut, like Pyronaut, can supply their own default logos.
     */
    public static String DARK_MODE_PNG_RESOURCE = "io/micronaut/context/banner/micronaut-banner/logo.png";

    /**
     * Path to the bitmap for the light mode version of the logo (i.e. black on white).
     * This is public and mutable so variants of Micronaut, like Pyronaut, can supply their own default logos.
     */
    public static String LIGHT_MODE_PNG_RESOURCE = "io/micronaut/context/banner/micronaut-banner/logo-light.png";

    private final PrintStream out;

    /**
     * Constructor.
     *
     * @param out The print stream
     */
    public MicronautBanner(PrintStream out) {
        this.out = out;
    }

    @Override
    public void print() {
        if (maybePrintInlineTerminalImage())
            return;
        for (String line : MICRONAUT_BANNER) {
            out.println(line);
        }
    }

    private boolean maybePrintInlineTerminalImage() {
        if (!kittyImageEnabled()) {
            return false;
        }
        try (InputStream stream = openBannerFor(bannerTheme())) {
            if (stream == null) {
                return false;
            }
            out.println();
            out.print("\u001B_Ga=T,f=100,q=1;");
            printBase64Of(stream);
            out.print("\u001B\\");
            out.println();
            return true;
        } catch (IOException ignored) {
            // Fall back to the text banner when an image resource cannot be read.
        }
        return false;
    }

    private void printBase64Of(InputStream stream) throws IOException {
        out.print(Base64.getEncoder().encodeToString(stream.readAllBytes()));
    }

    private static @Nullable InputStream openBannerFor(BannerTheme theme) {
        boolean light = theme == BannerTheme.LIGHT;
        String customResource = light ? CUSTOM_LIGHT_PNG_RESOURCE : CUSTOM_PNG_RESOURCE;
        String defaultResource = light ? LIGHT_MODE_PNG_RESOURCE : DARK_MODE_PNG_RESOURCE;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MicronautBanner.class.getClassLoader();
        }
        InputStream imageStream = classLoader.getResourceAsStream(customResource);
        if (imageStream == null) {
            imageStream = classLoader.getResourceAsStream(defaultResource);
        }
        return imageStream;
    }

    private static boolean kittyImageEnabled() {
        String mode = System.getenv(BANNER_MODE_ENV);
        if (mode != null) {
            return switch (mode.trim().toLowerCase(Locale.ROOT)) {
                case "ascii" -> false;
                case "kitty" -> true;
                case "", "auto" -> detectedKittyTerminal();
                default -> false;
            };
        }
        return detectedKittyTerminal();
    }

    private static BannerTheme bannerTheme() {
        String theme = System.getenv(BANNER_THEME_ENV);
        if (theme != null) {
            return switch (theme.trim().toLowerCase(Locale.ROOT)) {
                case "light" -> BannerTheme.LIGHT;
                case "dark" -> BannerTheme.DARK;
                case "", "auto" -> detectedBannerTheme();
                default -> BannerTheme.DARK;
            };
        }
        return detectedBannerTheme();
    }

    private static BannerTheme detectedBannerTheme() {
        String colorFgBg = System.getenv("COLORFGBG");
        if (colorFgBg == null) {
            return BannerTheme.DARK;
        }
        String[] colors = colorFgBg.split(";");
        if (colors.length < 2) {
            return BannerTheme.DARK;
        }
        try {
            int backgroundColor = Integer.parseInt(colors[colors.length - 1]);
            return backgroundColor == 7 || backgroundColor == 15 ? BannerTheme.LIGHT : BannerTheme.DARK;
        } catch (NumberFormatException ignored) {
            return BannerTheme.DARK;
        }
    }

    private static boolean detectedKittyTerminal() {
        if (!isTerminal()) {
            return false;
        }
        // There is unfortunately no good way to detect terminal capabilities.
        String term = System.getenv("TERM");
        String termProgram = System.getenv("TERM_PROGRAM");
        String kittyWindowId = System.getenv("KITTY_WINDOW_ID");
        String lowerTerm = term == null ? "" : term.toLowerCase(Locale.ROOT);
        if (kittyWindowId != null || lowerTerm.contains("kitty")) {
            return true;
        }
        if ("iTerm.app".equals(termProgram) || "WezTerm".equals(termProgram)) {
            return true;
        }
        return false;
    }

    @SuppressWarnings({"NoReflection", "SystemConsoleNull"}) // The JDK 22 API must remain optional for older runtime versions.
    private static boolean isTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try {
            return (boolean) Console.class.getMethod("isTerminal").invoke(console);
        } catch (NoSuchMethodException e) {
            // On Java versions before 22, a non-null system console is the available terminal check.
            return true;
        } catch (ReflectiveOperationException | SecurityException e) {
            return false;
        }
    }

    private enum BannerTheme {
        LIGHT,
        DARK
    }
}
