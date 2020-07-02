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
package io.micronaut.http.context.event;

import io.micronaut.context.event.ApplicationEvent;
import io.micronaut.http.HttpRequest;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An event fired when an {@link HttpRequest} is finalized by the server. Note that the event is fired asynchronously and
 * consumers of the event should generally not perform I/O, instead this designed for tracing of headers, URI etc.
 *
 * @author graemerocher
 * @since 1.2.0
 */
public class HttpRequestTerminatedEvent extends ApplicationEvent {

    /**
     * @param request The request. Never null.
     */
    public HttpRequestTerminatedEvent(@NonNull HttpRequest<?> request) {
        super(request);
    }

    @Override
    @NonNull
    public HttpRequest<?> getSource() {
        return (HttpRequest<?>) super.getSource();
    }
}
