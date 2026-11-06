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

import io.micronaut.core.io.ResourceLoader;

import java.io.PrintStream;

/**
 * Default implementation of {@link Banner} that prints the default Micronaut banner: the logo when
 * the terminal displays images, otherwise ASCII art.
 */
public class MicronautBanner implements Banner {

    private static final String[] MICRONAUT_BANNER = {
            " __  __ _                                  _   ",
            "|  \\/  (_) ___ _ __ ___  _ __   __ _ _   _| |_ ",
            "| |\\/| | |/ __| '__/ _ \\| '_ \\ / _` | | | | __|",
            "| |  | | | (__| | | (_) | | | | (_| | |_| | |_ ",
            "|_|  |_|_|\\___|_|  \\___/|_| |_|\\__,_|\\__,_|\\__|"
    };

    private final PrintStream out;
    private final BannerTerminal terminal;

    /**
     * Constructor.
     *
     * @param out The print stream
     */
    public MicronautBanner(PrintStream out) {
        this(out, BannerMode.AUTO);
    }

    /**
     * Constructor.
     *
     * @param out The print stream
     * @param mode The banner mode
     * @since 5.3.0
     */
    public MicronautBanner(PrintStream out, BannerMode mode) {
        this(out, BannerTerminal.current(mode));
    }

    /**
     * Constructor.
     *
     * @param out The print stream
     * @param terminal The terminal
     */
    MicronautBanner(PrintStream out, BannerTerminal terminal) {
        this.out = out;
        this.terminal = terminal;
    }

    /**
     * Finds the banner of the application: its image, otherwise its text, otherwise the default banner.
     *
     * @param resourceLoader The loader of the banners of the application
     * @param out The print stream
     * @param mode The banner mode
     * @return The banner
     * @since 5.3.0
     */
    public static Banner resolve(ResourceLoader resourceLoader, PrintStream out, BannerMode mode) {
        return ImageBanner.resolve(resourceLoader, out, mode)
            .or(() -> ResourceBanner.resolve(resourceLoader, out))
            .orElseGet(() -> new MicronautBanner(out, mode));
    }

    @Override
    public void print() {
        if (ImageBanner.printLogo(terminal, out)) {
            return;
        }
        for (String line : MICRONAUT_BANNER) {
            out.println(line);
        }
    }
}
