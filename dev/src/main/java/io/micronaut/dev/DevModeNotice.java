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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.http.DevAccessLinks;
import io.micronaut.dev.livereload.LiveReloadServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The note the launcher logs once, when a generation of the application first runs: the process runs in development
 * mode, not for production. For an HTTP server it adds the link that opens the development pages with the launch's
 * access token, and a warning when other machines can reach the server; it adds the LiveReload server's port when it
 * runs.
 *
 * @since 5.3.0
 */
@Internal
final class DevModeNotice {

    static final String HEADLINE = "Development Mode Active: Not for production usage.";

    private final AtomicBoolean logged = new AtomicBoolean();

    /**
     * Logs the note, after the application's own startup lines, the first time a generation runs: on the first start,
     * or on the reload that recovers from a first start that failed. A reload after that logs a line of its own, not the
     * note again.
     *
     * @param log        The launcher's logger
     * @param liveReload The LiveReload server, when it runs
     * @param http       Whether the application defines an HTTP server
     * @param generation The generation that runs
     * @param access     The access of the launch, whose token the link carries
     */
    void logOnce(Logger log, @Nullable LiveReloadServer liveReload, boolean http, ApplicationContext generation, DevAccess access) {
        if (logged.compareAndSet(false, true) && log.isInfoEnabled()) {
            OptionalInt port = liveReload == null ? OptionalInt.empty() : OptionalInt.of(liveReload.port());
            DevAccessLinks.Link link = http ? DevAccessLinks.of(generation, access) : null;
            log.info("{}", text(port, link));
        }
    }

    /**
     * The note for a launch.
     *
     * @param liveReload The port the LiveReload server listens on, when it runs
     * @param link       The link to the development pages and the server's address, when a server runs
     * @return The note: the headline, then a line each for the link, the warning and the LiveReload server
     */
    static String text(OptionalInt liveReload, DevAccessLinks.@Nullable Link link) {
        StringBuilder text = new StringBuilder(HEADLINE);
        if (link != null) {
            // the only place the token is shown: without it the development pages cannot be opened
            line(text, "Development pages: " + link.url());
            if (link.reachable()) {
                line(text, "HTTP server listening on " + link.bindAddress()
                    + ", reachable from other machines: set micronaut.server.host=localhost to restrict it");
            }
        }
        if (liveReload.isPresent()) {
            line(text, "LiveReload server listening on port " + liveReload.getAsInt());
        }
        return text.toString();
    }

    private static void line(StringBuilder text, String line) {
        text.append(System.lineSeparator()).append(line);
    }
}
