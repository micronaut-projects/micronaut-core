/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.annotation.Nonnull;

/**
 * An event fired when an {@link HttpRequest} is received by the server. Not that the event is fired in a
 * non-blocking manner and access to the request body is not provided. Consumers can use this event to
 * trace the URI, headers and so on but should not perform I/O.
 *
 * @author graemerocher
 * @since 1.2.0
 */
public class HttpRequestReceivedEvent extends ApplicationEvent {

    /**
     * @param request The request. Never null.
     */
    public HttpRequestReceivedEvent(@Nonnull HttpRequest<?> request) {
        super(request);
    }

    @Override
    @Nonnull
    public HttpRequest<?> getSource() {
        return (HttpRequest<?>) super.getSource();
    }
}
