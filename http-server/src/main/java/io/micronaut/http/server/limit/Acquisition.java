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
package io.micronaut.http.server.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpStatus;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The answer of a {@link RequestLimiter}: a granted permit, or a rejection.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface Acquisition {

    /**
     * A granted permit whose release action runs at most once, however often it is released.
     *
     * @param release The action that gives the permit back
     * @return The granted permit
     */
    static Permit granted(Runnable release) {
        return new Permit(Objects.requireNonNull(release, "release"));
    }

    /**
     * A rejection.
     *
     * @param status     The status of the response, usually 429 or 503
     * @param retryAfter When the client may try again, or {@code null} if unknown
     * @return The rejection
     */
    static Rejected rejected(HttpStatus status, @Nullable Duration retryAfter) {
        return new Rejected(status, retryAfter);
    }

    /**
     * A granted permit.
     */
    final class Permit implements Acquisition {
        private final Runnable release;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Runnable release) {
            this.release = release;
        }

        /**
         * Give the permit back. Only the first call has an effect.
         */
        public void release() {
            if (released.compareAndSet(false, true)) {
                release.run();
            }
        }

        /**
         * @return Whether the permit was released
         */
        public boolean isReleased() {
            return released.get();
        }
    }

    /**
     * A rejection.
     *
     * @param status     The status of the response
     * @param retryAfter When the client may try again, or {@code null} if unknown
     */
    record Rejected(HttpStatus status, @Nullable Duration retryAfter) implements Acquisition {
        /**
         * @param status     The status of the response
         * @param retryAfter When the client may try again, or {@code null} if unknown
         */
        public Rejected {
            Objects.requireNonNull(status, "status");
        }
    }
}
