/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;

/**
 * {@link io.micronaut.http.body.stream.BufferConsumer.Upstream} implementation that stores any
 * inputs and then {@link #forward forwards} them to another upstream later on. This helps with
 * reentrant calls to the subscriber during a subscribe call, when the real upstream is not yet
 * available.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public final class LazyUpstream implements BufferConsumer.Upstream {
    private boolean start;
    private boolean allowDiscard;
    private boolean disregardBackpressure;
    private long consumed;

    @Override
    public void start() {
        this.start = true;
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
        long newConsumed = consumed + bytesConsumed;
        if (newConsumed < 0) {
            newConsumed = Long.MAX_VALUE;
        }
        consumed = newConsumed;
    }

    @Override
    public void allowDiscard() {
        this.allowDiscard = true;
    }

    @Override
    public void disregardBackpressure() {
        this.disregardBackpressure = true;
    }

    public void forward(BufferConsumer.Upstream actual) {
        if (consumed != 0) {
            actual.onBytesConsumed(consumed);
        }
        if (start) {
            actual.start();
        }
        if (allowDiscard) {
            actual.allowDiscard();
        }
        if (disregardBackpressure) {
            actual.disregardBackpressure();
        }
    }
}
