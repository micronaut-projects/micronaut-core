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
package io.micronaut.dev.livereload;

import io.micronaut.core.annotation.Experimental;

import java.io.Closeable;

/**
 * A running LiveReload server: browsers connected to it reload the page, or swap a stylesheet, on
 * request. The implementation comes from a {@link LiveReloadServerFactory} on the development
 * runtime classpath, {@code micronaut-dev-livereload}; without one there is no server.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface LiveReloadServer extends Closeable {

    /**
     * The port the LiveReload extensions expect.
     */
    int DEFAULT_PORT = 35729;

    /**
     * The path of the client script the server serves.
     */
    String SCRIPT_PATH = "/livereload.js";

    /**
     * @return The port bound
     */
    int port();

    /**
     * @return How many browsers are connected
     */
    int connections();

    /**
     * Asks every connected browser to reload.
     *
     * @param path The path that changed, {@code /} for the page itself
     * @param liveCss Whether the path is a stylesheet the browser can swap without reloading the page
     */
    void reload(String path, boolean liveCss);

    /**
     * Stops the server and disconnects the browsers.
     */
    @Override
    void close();

    /**
     * The script tag a page includes to connect to a server on the given port.
     *
     * @param port The port
     * @return The tag
     */
    static String scriptTag(int port) {
        return "<script src=\"http://localhost:" + port + SCRIPT_PATH + "?snipver=1\" async></script>";
    }
}
