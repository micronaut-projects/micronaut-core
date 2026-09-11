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
package io.micronaut.http.server.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.function.LongFunction;

/**
 * Caches a string that is a pure function of the wall clock truncated to whole seconds, so that
 * callers on many threads that need the same value within the same second only compute it once.
 * <p>
 * Thread safety: the cache is a single {@code volatile} reference to an immutable
 * (second, text) pair. A reader loads the reference once and either returns the text, if the
 * pair's second matches its own clock reading, or computes the text for its own second and
 * publishes a new pair. Concurrent readers that both miss compute the same text for the same
 * second and publish equivalent pairs; a reader that publishes a stale pair after a newer one
 * only causes the next reader to recompute. In every interleaving a reader returns the text for
 * the second it observed on the clock, never a torn or mixed value.
 *
 * @since 5.2.0
 */
@Internal
public final class PerSecondCache {
    private final LongFunction<String> formatter;
    @Nullable
    private volatile Entry cached;

    /**
     * Create a new cache.
     *
     * @param formatter Computes the text for a given epoch second
     */
    public PerSecondCache(LongFunction<String> formatter) {
        this.formatter = formatter;
    }

    /**
     * The text for the current wall-clock time.
     *
     * @return The text for the current second
     */
    public String now() {
        return get(System.currentTimeMillis());
    }

    /**
     * The text for the given instant.
     *
     * @param epochMillis The instant, in milliseconds since the epoch
     * @return The text for the second that contains the instant
     */
    public String get(long epochMillis) {
        long second = Math.floorDiv(epochMillis, 1000);
        Entry entry = cached;
        if (entry != null && entry.second == second) {
            return entry.text;
        }
        String text = formatter.apply(second);
        cached = new Entry(second, text);
        return text;
    }

    private record Entry(long second, String text) {
    }
}
