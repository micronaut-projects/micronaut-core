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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import org.jspecify.annotations.Nullable;

/**
 * A request a gateway route could not relay: the upstream could not be reached, did not answer
 * in time, or failed before the headers of its response arrived. It is answered like any
 * exception of a route, with its status unless an error route of the application handles it:
 * <ul>
 *     <li>{@code 502 Bad Gateway}: the connection was refused or reset, the name of the host did
 *     not resolve, or the upstream closed the connection before the headers;</li>
 *     <li>{@code 504 Gateway Timeout}: the headers did not arrive in time;</li>
 *     <li>{@code 503 Service Unavailable}: the service has no available instance, or the circuit
 *     breaker of the route is open and the route has no fallback.</li>
 * </ul>
 * A failure after the headers arrived cannot change the response any more: the body the client
 * receives is cut short.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class UpstreamException extends HttpStatusException {

    private final String routeId;

    /**
     * @param status  The status of the response
     * @param routeId The id of the gateway route
     * @param message The message
     * @param cause   The failure of the upstream exchange, if any
     */
    public UpstreamException(HttpStatus status, String routeId, String message, @Nullable Throwable cause) {
        super(status, message);
        this.routeId = routeId;
        if (cause != null) {
            initCause(cause);
        }
    }

    /**
     * @return The id of the gateway route
     */
    public String getRouteId() {
        return routeId;
    }
}
