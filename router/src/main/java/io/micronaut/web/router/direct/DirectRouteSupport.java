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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;

/**
 * The capability of a server runtime to answer direct routes: a runtime that looks up the
 * {@link DirectRouteLookup} for each request it receives, as {@link DirectRouteLookup} describes, declares
 * a bean of this type, e.g. a {@code @Singleton} of its module. When the
 * application declares a direct route and no bean of this type exists, e.g. on a runtime that
 * has no hook to answer a request before it creates the {@link io.micronaut.http.HttpRequest},
 * the routes fail to build, and the application fails to start: a direct route is never served
 * as an ordinary route, with the filters it was declared to skip.
 *
 * <p>Its presence is the capability: a runtime that declares it answers every direct route, the
 * asynchronous ones included, see {@link PendingResponse}. Its one method, with a default, lets
 * the runtime prepare the bodies the requests of a route share.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectRouteSupport {

    /**
     * Prepare the body of a response given as a value to a direct route, see
     * {@link io.micronaut.web.router.builder.DirectRouteSpec#respond(io.micronaut.http.HttpResponse)}:
     * the route shares it with every response it creates, so the runtime writes it once per
     * request. It is called once per route, when the routes are built. A runtime that consumes a
     * body of its own type when it writes it, e.g. a buffer it releases once written, returns a
     * copy it can write any number of times, e.g. the bytes of the buffer, and releases the body.
     *
     * @param body The body, neither text nor a {@code byte[]}, which the router encodes or
     *             copies itself
     * @return The body the responses of the route share: by default the body itself
     */
    default Object shareableBody(Object body) {
        return body;
    }
}
