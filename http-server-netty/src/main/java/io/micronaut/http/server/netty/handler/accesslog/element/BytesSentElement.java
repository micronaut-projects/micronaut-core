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

/**
 * BytesSentElement LogElement. The bytes sent.
 *
 * @author croudet
 * @since 2.0
 */
final class BytesSentElement implements LogElement {
    /**
     * The bytes sent marker (set dask when 0.)
     */
    public static final String BYTES_SENT_DASH = "b";
    /**
     * The bytes sent marker.
     */
    public static final String BYTES_SENT = "B";

    private static final Set<Event> EVENTS = Collections.unmodifiableSet(EnumSet.of(Event.ON_RESPONSE_WRITE, Event.ON_LAST_RESPONSE_WRITE));

    private final boolean dashIfZero;
    private long bytesSent;

    /**
     * Creates a BytesSentElement.
     * @param dashIfZero When true, use '-' when bytes sent is 0.
     */
    BytesSentElement(final boolean dashIfZero) {
        this.dashIfZero = dashIfZero;
    }

    @Override
    public void onResponseWrite(int contentSize) {
        bytesSent += contentSize;
    }

    @Override
    public String onLastResponseWrite(int contentSize) {
        bytesSent += contentSize;
        return dashIfZero && bytesSent == 0L ? ConstantElement.UNKNOWN_VALUE : Long.toString(bytesSent);
    }

    @Override
    public Set<Event> events() {
        return EVENTS;
    }

    @Override
    public LogElement copy() {
        return new BytesSentElement(dashIfZero);
    }

    @Override
    public void reset() {
        bytesSent = 0L;
    }

    @Override
    public String toString() {
        return '%' + (dashIfZero ? BYTES_SENT_DASH : BYTES_SENT);
    }
}
