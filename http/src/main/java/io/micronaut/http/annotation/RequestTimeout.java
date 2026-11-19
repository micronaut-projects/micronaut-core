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
package io.micronaut.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The time the server gives a route to produce its response: from when the route starts, after
 * the request filters, including the time its arguments wait for the request body, until the
 * route produced the response, i.e. its status and headers. A streamed body, e.g. server-sent
 * events, is not limited once the response was produced. On expiry, the route is cancelled and
 * the request is answered with a {@code 503 Service Unavailable} through the error handling,
 * with a {@code io.micronaut.http.server.exceptions.RequestTimeoutException}.
 *
 * <p>On a controller the timeout applies to all of its routes, and on a method it overrides the
 * one of the controller. It overrides the default of the server,
 * {@code micronaut.server.request-timeout}. A duration of zero or a negative duration disables
 * the timeout for the route.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequestTimeout {

    /**
     * @return The timeout, a duration such as {@code 10s} or {@code 500ms}; {@code 0} disables it
     */
    String value();
}
