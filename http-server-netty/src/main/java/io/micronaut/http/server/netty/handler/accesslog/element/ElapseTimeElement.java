/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.handler.accesslog.element;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpHeaders;

/**
 * ElapseTimeElement LogElement. Time spent to complete the request.
 *
 * @author croudet
 * @since 2.0
 */
final class ElapseTimeElement implements LogElement {
    /**
     * The elapse time marker (seconds.)
     */
    public static final String ELAPSE_TIME_SECONDS = "T";
    /**
     * The elapse time marker (milliseconds.)
     */
    public static final String ELAPSE_TIME_MILLIS = "D";

    private static final Set<Event> EVENTS = Collections.unmodifiableSet(EnumSet.of(Event.ON_REQUEST_HEADERS, Event.ON_LAST_RESPONSE_WRITE));

    private final boolean inSeconds;
    private long start;

    /**
     * Create an ElapseTimeElement.
     *
     * @param inSeconds When true time is will be printed in seconds otherwise in millisecond.
     */
    ElapseTimeElement(final boolean inSeconds) {
        this.inSeconds = inSeconds;
    }

    @Override
    public String onRequestHeaders(SocketChannel channel, String method, HttpHeaders headers, String uri, String protocol) {
        start = System.nanoTime();
        return null;
    }

    @Override
    public String onLastResponseWrite(int contentSize) {
        final long elapseTime = inSeconds ? TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return Long.toString(elapseTime);
    }

    @Override
    public Set<Event> events() {
        return EVENTS;
    }

    @Override
    public LogElement copy() {
        return new ElapseTimeElement(inSeconds);
    }

    @Override
    public void reset() {
        start = 0L;
    }

    @Override
    public String toString() {
        return '%' + (inSeconds ? ELAPSE_TIME_SECONDS : ELAPSE_TIME_MILLIS);
    }
}
