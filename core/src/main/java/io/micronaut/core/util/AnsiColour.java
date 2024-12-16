/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.core.util;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Ansi color coding.
 *
 * @since 4.8.0
 */
public enum AnsiColour {
    //Color end string, color reset
    RESET("\033[0m"),

    // Regular Colors. Normal color, no bold, background color etc.
    BLACK("\033[0;30m"),    // BLACK
    RED("\033[0;31m"),      // RED
    GREEN("\033[0;32m"),    // GREEN
    YELLOW("\033[0;33m"),   // YELLOW
    BLUE("\033[0;34m"),     // BLUE
    MAGENTA("\033[0;35m"),  // MAGENTA
    CYAN("\033[0;36m"),     // CYAN
    WHITE("\033[0;37m"),    // WHITE

    // Bold
    BLACK_BOLD("\033[1;30m"),   // BLACK
    RED_BOLD("\033[1;31m"),     // RED
    GREEN_BOLD("\033[1;32m"),   // GREEN
    YELLOW_BOLD("\033[1;33m"),  // YELLOW
    BLUE_BOLD("\033[1;34m"),    // BLUE
    MAGENTA_BOLD("\033[1;35m"), // MAGENTA
    CYAN_BOLD("\033[1;36m"),    // CYAN
    WHITE_BOLD("\033[1;37m"),   // WHITE

    // Underline
    BLACK_UNDERLINED("\033[4;30m"),     // BLACK
    RED_UNDERLINED("\033[4;31m"),       // RED
    GREEN_UNDERLINED("\033[4;32m"),     // GREEN
    YELLOW_UNDERLINED("\033[4;33m"),    // YELLOW
    BLUE_UNDERLINED("\033[4;34m"),      // BLUE
    MAGENTA_UNDERLINED("\033[4;35m"),   // MAGENTA
    CYAN_UNDERLINED("\033[4;36m"),      // CYAN
    WHITE_UNDERLINED("\033[4;37m"),     // WHITE

    // Background
    BLACK_BACKGROUND("\033[40m"),   // BLACK
    RED_BACKGROUND("\033[41m"),     // RED
    GREEN_BACKGROUND("\033[42m"),   // GREEN
    YELLOW_BACKGROUND("\033[43m"),  // YELLOW
    BLUE_BACKGROUND("\033[44m"),    // BLUE
    MAGENTA_BACKGROUND("\033[45m"), // MAGENTA
    CYAN_BACKGROUND("\033[46m"),    // CYAN
    WHITE_BACKGROUND("\033[47m"),   // WHITE

    // High Intensity
    BLACK_BRIGHT("\033[0;90m"),     // BLACK
    RED_BRIGHT("\033[0;91m"),       // RED
    GREEN_BRIGHT("\033[0;92m"),     // GREEN
    YELLOW_BRIGHT("\033[0;93m"),    // YELLOW
    BLUE_BRIGHT("\033[0;94m"),      // BLUE
    MAGENTA_BRIGHT("\033[0;95m"),   // MAGENTA
    CYAN_BRIGHT("\033[0;96m"),      // CYAN
    WHITE_BRIGHT("\033[0;97m"),     // WHITE

    // Bold High Intensity
    BLACK_BOLD_BRIGHT("\033[1;90m"),    // BLACK
    RED_BOLD_BRIGHT("\033[1;91m"),      // RED
    GREEN_BOLD_BRIGHT("\033[1;92m"),    // GREEN
    YELLOW_BOLD_BRIGHT("\033[1;93m"),   // YELLOW
    BLUE_BOLD_BRIGHT("\033[1;94m"),     // BLUE
    MAGENTA_BOLD_BRIGHT("\033[1;95m"),  // MAGENTA
    CYAN_BOLD_BRIGHT("\033[1;96m"),     // CYAN
    WHITE_BOLD_BRIGHT("\033[1;97m"),    // WHITE

    // High Intensity backgrounds
    BLACK_BACKGROUND_BRIGHT("\033[0;100m"),     // BLACK
    RED_BACKGROUND_BRIGHT("\033[0;101m"),       // RED
    GREEN_BACKGROUND_BRIGHT("\033[0;102m"),     // GREEN
    YELLOW_BACKGROUND_BRIGHT("\033[0;103m"),    // YELLOW
    BLUE_BACKGROUND_BRIGHT("\033[0;104m"),      // BLUE
    MAGENTA_BACKGROUND_BRIGHT("\033[0;105m"),   // MAGENTA
    CYAN_BACKGROUND_BRIGHT("\033[0;106m"),      // CYAN
    WHITE_BACKGROUND_BRIGHT("\033[0;107m");     // WHITE

    private final String code;

    AnsiColour(String code) {
        this.code = code;
    }

    /**
     * Highlight cyan if supported.
     * @param text The text
     * @return the string
     */
    public static String cyan(String text) {
        if (isSupported()) {
            return AnsiColour.CYAN + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Highlight bright cyan if supported.
     * @param text The text
     * @return the string
     */
    public static String brightCyan(String text) {
        if (isSupported()) {
            return AnsiColour.CYAN_BRIGHT + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Highlight in yellow.
     * @param text The text
     * @return The formatted string
     */
    public static String yellow(@NonNull String text) {
        if (isSupported()) {
            return AnsiColour.YELLOW + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Highlight in bright blue.
     * @param text The text
     * @return The formatted string
     */
    public static String brightBlue(String text) {
        if (isSupported()) {
            return AnsiColour.BLUE_BRIGHT + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Output in magenta bold.
     * @param text The text
     * @return The formatted text.
     */
    public static String magentaBold(String text) {
        if (isSupported()) {
            return AnsiColour.MAGENTA_BOLD + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Output green.
     * @param text The text
     * @return The formatted text
     */
    public static String green(String text) {
        if (isSupported()) {
            return AnsiColour.GREEN + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Output bright yellow.
     * @param text The text
     * @return The formatted text
     */
    public static String brightYellow(String text) {
        if (isSupported()) {
            return AnsiColour.YELLOW_BRIGHT + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    /**
     * Format an object for display.
     * @param object The object
     * @return The formatted object
     */
    public static @NonNull String formatObject(@Nullable Object object) {
        if (object instanceof CharSequence charSequence) {
            return green("\"" + charSequence + "\"");
        } else if (object instanceof Number number) {
            return brightBlue(number.toString());
        } else if (object == null) {
            return brightBlue("null");
        } else {
            return brightYellow(object.toString());
        }
    }

    /**
     * Format blue.
     * @param text The text
     * @return The formatted text
     */
    public static @NonNull String blue(@NonNull String text) {
        if (isSupported()) {
            return AnsiColour.BLUE + text + AnsiColour.RESET;
        } else {
            return text;
        }
    }

    @Override
    public String toString() {
        return code;
    }

    /**
     * Are ANSI colors supported.
     * @return True if they are
     */
    public static boolean isSupported() {
        String os = System.getProperty("os.name").toLowerCase();
        return !os.contains("win") || System.console() != null;
    }
}
