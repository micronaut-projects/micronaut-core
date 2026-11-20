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

/**
 * How the startup banner is displayed. The {@code MICRONAUT_BANNER_MODE} environment variable takes
 * precedence over the mode set by the application.
 *
 * @since 5.3.0
 */
public enum BannerMode {

    /**
     * Displays an image when the terminal is detected to support it, otherwise text.
     */
    AUTO,

    /**
     * Always displays an image with the Kitty graphics protocol, without checking the terminal.
     */
    KITTY,

    /**
     * Always displays text.
     */
    ASCII,

    /**
     * Displays an animated image when the terminal is detected to support images, otherwise text,
     * like {@link #AUTO}. The banner is {@code META-INF/micronaut/banner.svg}, which may be animated
     * with SMIL, in preference to {@code banner.png}, and the default banner is the animated logo.
     */
    ANIMATED
}
