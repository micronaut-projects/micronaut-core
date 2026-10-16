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

import io.micronaut.core.annotation.Internal;

/**
 * Where the LiveReload client script is served and the tag a page includes to load it, shared by the filter that
 * injects the tag and the server that serves the script. They are no part of the {@link LiveReloadServer} contract,
 * and live here rather than in the filter because the server runs in the launcher, where the HTTP API the filter is
 * written against is absent.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
public final class LiveReloadScript {

    /**
     * The port the LiveReload browser extensions expect.
     */
    public static final int DEFAULT_PORT = 35729;

    /**
     * The path of the client script.
     */
    public static final String PATH = "/livereload.js";

    private LiveReloadScript() {
    }

    /**
     * The script tag a page includes to connect to a server on the given port.
     *
     * @param port The port
     * @return The tag
     */
    public static String tag(int port) {
        return "<script src=\"http://localhost:" + port + PATH + "?snipver=1\" async></script>";
    }
}
