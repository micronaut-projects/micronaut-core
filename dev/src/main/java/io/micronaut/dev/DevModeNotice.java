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

import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.livereload.LiveReloadServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The note the launcher logs once, when a generation of the application first runs: the process runs in development
 * mode, not for production, and where the LiveReload server listens when it runs.
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
     */
    void logOnce(Logger log, @Nullable LiveReloadServer liveReload) {
        if (logged.compareAndSet(false, true) && log.isInfoEnabled()) {
            log.info("{}", text(liveReload == null ? OptionalInt.empty() : OptionalInt.of(liveReload.port())));
        }
    }

    /**
     * The note for a launch.
     *
     * @param liveReload The port the LiveReload server listens on, when it runs
     * @return The note: the headline, and the LiveReload server's port on a line of its own when it runs
     */
    static String text(OptionalInt liveReload) {
        StringBuilder text = new StringBuilder(HEADLINE);
        if (liveReload.isPresent()) {
            text.append(System.lineSeparator()).append("LiveReload server listening on port ").append(liveReload.getAsInt());
        }
        return text.toString();
    }
}
