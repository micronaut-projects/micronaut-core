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
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;

/**
 * Observes the exchanges of the gateway routes, e.g. to record a timer per route, method,
 * status and outcome, or to end a tracing span. Every bean of this type is told of every exchange
 * of a {@link ProxyHandler}, once, when it ends: when the response headers are ready to be sent
 * to the client, when the relay failed, or when the client went away first. The duration is the
 * time until then: the body of the response streams after it, and is not part of it.
 * <p>An observer runs on the thread that ends the exchange, often an event loop: it must not
 * block. A failure of an observer is logged and does not change the response.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface GatewayObserver {

    /**
     * @param exchange The exchange that ended
     */
    void exchanged(Exchange exchange);

    /**
     * How an exchange ended.
     */
    enum Outcome {
        /**
         * A response, of the upstream or of a fallback, is relayed.
         */
        RESPONSE,
        /**
         * The relay failed, e.g. the upstream could not be reached, or timed out, or a filter
         * refused the request.
         */
        FAILURE,
        /**
         * The client went away before the response was ready.
         */
        CANCELLED
    }

    /**
     * An exchange of a gateway route.
     *
     * @param routeId  The id of the route; a bounded tag, unlike the path
     * @param method   The method of the request
     * @param status   The status of the response, or of the error of a failure, e.g. 502 or
     *                 504; 0 if there is none, e.g. for a cancel
     * @param outcome  How it ended
     * @param duration The time from the start of the relay until it ended
     * @param upstream The URI of the upstream of the last attempt, or {@code null} if none was
     *                 made
     * @param failure  The failure, or {@code null}
     */
    record Exchange(String routeId,
                    String method,
                    int status,
                    Outcome outcome,
                    Duration duration,
                    @Nullable URI upstream,
                    @Nullable Throwable failure) {
    }
}
