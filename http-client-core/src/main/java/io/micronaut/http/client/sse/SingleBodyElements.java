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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.PulledBodyElements;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.sse.Event;

import java.util.function.Function;

/**
 * A body that is not an event stream, as one event: buffered and decoded whole on the first
 * read. An empty body has no event.
 *
 * @param <B> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class SingleBodyElements<B> extends PulledBodyElements<Event<B>> {

    private final CloseableByteBody body;
    private final Function<byte[], B> reader;

    // guarded by this
    private boolean started;

    /**
     * @param body   The body
     * @param reader Decodes the body
     */
    SingleBodyElements(CloseableByteBody body, Function<byte[], B> reader) {
        this.body = body;
        this.reader = reader;
    }

    @Override
    protected void demand() {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
        }
        // bounded by the buffer limit of the body
        InternalByteBody.bufferFlow(body).onComplete((available, error) -> {
            if (error != null) {
                fail(EventStreams.wrap(error));
                return;
            }
            try (CloseableAvailableByteBody bytes = available) {
                if (bytes.length() > 0) {
                    push(Event.of(reader.apply(bytes.toByteArray())));
                }
                end();
            } catch (Throwable e) {
                fail(EventStreams.wrap(e));
            }
        });
    }

    @Override
    protected void release() {
        boolean close;
        synchronized (this) {
            close = !started;
            started = true;
        }
        if (close) {
            body.close();
        }
    }
}
