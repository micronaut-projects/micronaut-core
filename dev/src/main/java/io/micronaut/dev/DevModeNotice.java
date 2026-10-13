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
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.dev.manifest.DevReloadStrategy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.http.DevAccessLinks;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.dev.manifest.DevManifest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The note the launcher logs once, when a generation of the application first runs: the process runs with development
 * settings, not production ones. It lists only what is true of this launch, and, for an HTTP server, the link that
 * opens the development pages with the launch's access token and the address the server listens on.
 *
 * @since 5.3.0
 */
@Internal
final class DevModeNotice {

    static final String HEADLINE = "DEVELOPMENT MODE: not for production use";
    private static final String RULE = "=".repeat(80);

    private final AtomicBoolean logged = new AtomicBoolean();

    /**
     * Logs the note, after the application's own startup lines, the first time a generation runs: on the first start,
     * or on the reload that recovers from a first start that failed. A reload after that logs a line of its own, not the
     * note again.
     *
     * @param log        The launcher's logger
     * @param strategy   The reload strategy in force
     * @param liveReload The LiveReload server, when it runs
     * @param http       Whether the application defines an HTTP server, whose requests the gate filter answers
     * @param script     Whether the LiveReload script is added to the server's HTML pages
     * @param generation The generation that runs
     * @param access     The access of the launch, whose token the link carries
     */
    void logOnce(Logger log, DevReloadStrategy strategy, @Nullable LiveReloadServer liveReload, boolean http, boolean script,
                 ApplicationContext generation, DevAccess access) {
        if (logged.compareAndSet(false, true) && log.isInfoEnabled()) {
            OptionalInt port = liveReload == null ? OptionalInt.empty() : OptionalInt.of(liveReload.port());
            DevAccessLinks.Link link = http ? DevAccessLinks.of(generation, access) : null;
            log.info("{}", text(strategy, port, http, script, link));
        }
    }

    /**
     * The note for a launch.
     *
     * @param strategy   The reload strategy
     * @param liveReload The port the LiveReload server listens on, when it runs
     * @param http       Whether the application defines an HTTP server, whose requests the gate filter answers
     * @param script     Whether the LiveReload script is added to the server's HTML pages
     * @param link       The link to the development pages and the server's address, when a server runs
     * @return The note: a framed block of lines that starts on a line of its own
     */
    static String text(DevReloadStrategy strategy, OptionalInt liveReload, boolean http, boolean script, DevAccessLinks.@Nullable Link link) {
        StringBuilder text = new StringBuilder(System.lineSeparator());
        line(text, RULE);
        line(text, "  " + HEADLINE);
        line(text, "  The application runs under the micronaut-dev launcher, with development");
        line(text, "  settings rather than production ones:");
        line(text, "    - the '" + Environment.DEVELOPMENT + "' environment is active, with " + DevelopmentMode.PROPERTY + "=true");
        line(text, "    - classes and resources reload as they change (strategy: " + strategy.name().toLowerCase(Locale.ROOT) + ")");
        if (http) {
            line(text, "    - while a compilation fails, requests are answered with the compiler's");
            line(text, "      diagnostics, their source paths only with the access token");
        }
        if (liveReload.isPresent()) {
            line(text, "    - a LiveReload server listens on port " + liveReload.getAsInt() + (script ? ", and HTML pages load its script" : ""));
        }
        if (link != null) {
            if (link.allInterfaces()) {
                line(text, "    - the HTTP server listens on " + link.bindAddress() + ": other machines can reach it;");
                line(text, "      set micronaut.server.host=localhost to keep it to this one");
            } else {
                line(text, "    - the HTTP server listens on " + link.bindAddress());
            }
            if (link.tokenEnabled()) {
                line(text, "    - the development pages need this launch's access token; open:");
            } else {
                line(text, "    - the development pages need no access token (" + DevManifest.ACCESS_TOKEN_ENABLED + "=false); open:");
            }
            line(text, "      " + link.url());
        }
        line(text, "  Run the packaged application, without the launcher, in production.");
        return text.append(RULE).toString();
    }

    private static void line(StringBuilder text, String line) {
        text.append(line).append(System.lineSeparator());
    }
}
