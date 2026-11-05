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

import java.io.PrintStream;
import java.util.Optional;

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

    // The white logo, for the dark background terminals usually have
    private static final String LOGO = "io/micronaut/context/banner/micronaut-banner/logo.png";
    private static final String LOGO_LIGHT = "io/micronaut/context/banner/micronaut-banner/logo-light.png";
    // The logo is far larger than it is displayed so that it stays sharp on any display and font size
    private static final int LOGO_ROWS = 6;

    private final PrintStream out;
    private final BannerTerminal terminal;

    /**
     * Constructor.
     *
     * @param out The print stream
     */
    public MicronautBanner(PrintStream out) {
        this(out, BannerTerminal.current());
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

    @Override
    public void print() {
        if (printLogo()) {
            return;
        }
        for (String line : MICRONAUT_BANNER) {
            out.println(line);
        }
    }

    private boolean printLogo() {
        ClassLoader classLoader = MicronautBanner.class.getClassLoader();
        return terminal.selectImage(name -> Optional.ofNullable(classLoader.getResource(name)), LOGO, LOGO_LIGHT)
            .map(logo -> new ImageBanner(logo, out, LOGO_ROWS).printImage())
            .orElse(false);
    }
}
