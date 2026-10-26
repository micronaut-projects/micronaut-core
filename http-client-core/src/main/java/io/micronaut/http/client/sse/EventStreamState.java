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

import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * The reconnection state of an event stream: the last event id and the reconnection time, as
 * the <a href="https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation">event
 * stream interpretation</a> keeps them. Unlike the {@link io.micronaut.http.sse.Event events},
 * the state also follows an {@code id} or a {@code retry} field of a block without data, which
 * dispatches no event.
 *
 * <p>To follow the state of the stream of {@link SseClient#eventStream(io.micronaut.http.HttpRequest, io.micronaut.core.type.Argument)},
 * set an instance as the {@link #ATTRIBUTE} attribute of the request. The state is updated as
 * the stream is read, before the event of the same block is emitted, and can be read from any
 * thread. A state may be reused across the connections of one event source.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
public final class EventStreamState {

    /**
     * The name of the request attribute that holds the {@link EventStreamState} to update.
     *
     * @since 5.3.0
     */
    public static final String ATTRIBUTE = "micronaut.http.client.sse.state";

    private volatile @Nullable String lastEventId;
    private volatile @Nullable Duration reconnectionTime;

    /**
     * @return The last event id, set when a block ends, also without data; {@code null} until
     * an {@code id} field is read. An empty id resets it to the empty string.
     * @since 5.3.0
     */
    public @Nullable String getLastEventId() {
        return lastEventId;
    }

    /**
     * @return The reconnection time, set as soon as a valid {@code retry} field is read;
     * {@code null} until then
     * @since 5.3.0
     */
    public @Nullable Duration getReconnectionTime() {
        return reconnectionTime;
    }

    void lastEventId(@Nullable String id) {
        this.lastEventId = id;
    }

    void reconnectionTime(Duration retry) {
        this.reconnectionTime = retry;
    }
}
