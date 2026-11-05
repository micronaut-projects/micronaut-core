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

import java.io.Console;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * What the banners need to know about the terminal they print to: whether it displays images and
 * which image suits its theme.
 *
 * @param env The environment variables
 * @param interactive Whether the output is an interactive terminal, consulted last since opening the console is not free
 */
record BannerTerminal(Function<String, @Nullable String> env, BooleanSupplier interactive) {

    private static final String BANNER_MODE_ENV = "MICRONAUT_BANNER_MODE";
    private static final String BANNER_THEME_ENV = "MICRONAUT_BANNER_THEME";

    /**
     * @return The terminal of this process
     */
    static BannerTerminal current() {
        return new BannerTerminal(System::getenv, BannerTerminal::isConsoleInteractive);
    }

    /**
     * Selects the image for this terminal.
     *
     * @param lookup Finds a resource by name
     * @param resource The image, also used for a light theme when there is no light image
     * @param lightResource The image for a light theme
     * @return The image, empty if this terminal displays no images or the image does not exist
     */
    Optional<URL> selectImage(Function<String, Optional<URL>> lookup, String resource, String lightResource) {
        if (!supportsImages()) {
            return Optional.empty();
        }
        if (isLightTheme()) {
            Optional<URL> light = lookup.apply(lightResource);
            if (light.isPresent()) {
                return light;
            }
        }
        return lookup.apply(resource);
    }

    /**
     * @return Whether this terminal displays images sent with the Kitty graphics protocol
     */
    boolean supportsImages() {
        String mode = env.apply(BANNER_MODE_ENV);
        if (mode != null) {
            return switch (mode.trim().toLowerCase(Locale.ROOT)) {
                case "kitty" -> true;
                case "", "auto" -> isDetectedKittyTerminal();
                default -> false;
            };
        }
        return isDetectedKittyTerminal();
    }

    /**
     * @return Whether this terminal has a light background
     */
    boolean isLightTheme() {
        String theme = env.apply(BANNER_THEME_ENV);
        if (theme != null) {
            return switch (theme.trim().toLowerCase(Locale.ROOT)) {
                case "light" -> true;
                case "", "auto" -> isDetectedLightTheme();
                default -> false;
            };
        }
        return isDetectedLightTheme();
    }

    private boolean isDetectedLightTheme() {
        String colorFgBg = env.apply("COLORFGBG");
        if (colorFgBg == null) {
            return false;
        }
        String[] colors = colorFgBg.split(";");
        if (colors.length < 2) {
            return false;
        }
        try {
            int backgroundColor = Integer.parseInt(colors[colors.length - 1]);
            return backgroundColor == 7 || backgroundColor == 15;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private boolean isDetectedKittyTerminal() {
        // There is unfortunately no good way to detect terminal capabilities.
        String term = env.apply("TERM");
        String termProgram = env.apply("TERM_PROGRAM");
        boolean kitty = env.apply("KITTY_WINDOW_ID") != null
            || (term != null && term.toLowerCase(Locale.ROOT).contains("kitty"))
            || "iTerm.app".equals(termProgram)
            || "WezTerm".equals(termProgram);
        return kitty && interactive.getAsBoolean();
    }

    @SuppressWarnings({"NoReflection", "SystemConsoleNull"}) // The JDK 22 API must remain optional for older runtime versions.
    private static boolean isConsoleInteractive() {
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
}
