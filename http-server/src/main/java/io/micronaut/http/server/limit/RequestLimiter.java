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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.limit.LimiterState;

/**
 * Decides whether the server accepts a request now. The server asks the limiter for a permit
 * before the route runs, see {@link #tryAcquire(HttpRequest)}, and releases a granted permit once
 * the response is done: written, failed, or abandoned by the client.
 *
 * <p>The request is given to the limiter so that an implementation can partition its permits,
 * e.g. by the remote address of the client. Implementations must be thread safe and must not
 * block: the method runs on the event loop.</p>
 *
 * <p>A bean of this type is selected by its {@link #getName() name}, with
 * {@link RequestLimit} on a controller, or the {@link RequestLimit#ATTRIBUTE} attribute of a
 * functional route.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RequestLimiter extends LimiterState {

    /**
     * Try to acquire a permit for a request, without waiting.
     *
     * @param request The request
     * @return The granted permit, or the rejection
     */
    Acquisition tryAcquire(HttpRequest<?> request);
}
