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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;

/**
 * A route to a handler function, to configure after it was added with the {@link HttpRouteBuilder}.
 * A route on several HTTP methods configures all of them.
 *
 * <p>The filters of the route, see {@link RouteFilterSpec}, run after the application's filters and
 * the filters of the groups the route is declared in, closest to the route, and are resolved when
 * the route is built.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface HttpRouteSpec extends RouteFilterSpec<HttpRouteSpec> {

    /**
     * Accept requests with these media types only, like {@code @Consumes} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec consumes(MediaType... mediaTypes);

    /**
     * Accept requests with any media type.
     *
     * @return The route
     */
    HttpRouteSpec consumesAll();

    /**
     * Produce these media types, like {@code @Produces} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec produces(MediaType... mediaTypes);

    /**
     * Give the route annotations: the features that read the annotations of the matched route,
     * such as security rules, versioning, filter binding and the message body writers, see them
     * as if they were on a controller method, and so does the return type of the route. Typically
     * they are the annotations of the bean method the handler implements, from its
     * {@link io.micronaut.inject.ExecutableMethod}.
     *
     * @param annotationMetadata The annotations of the route
     * @return The route
     */
    HttpRouteSpec annotationMetadata(AnnotationMetadata annotationMetadata);

    /**
     * The route implements a bean method, e.g. a method of a resource that a framework
     * integration routes with handler functions: the route has the annotations of the method, see
     * {@link #annotationMetadata(AnnotationMetadata)}, and its target method, declaring type and
     * method name are the ones of the bean method. The arguments of the route stay those of the
     * handler.
     *
     * @param method The bean method
     * @return The route
     */
    HttpRouteSpec implementing(ExecutableMethod<?, ?> method);

    /**
     * Run the route on the named executor, like {@code @ExecuteOn} on a controller method. It
     * applies whatever the thread selection of the server.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route
     */
    HttpRouteSpec executeOn(String executorName);

    /**
     * Run the route on the event loop, like {@code @NonBlocking} on a controller method, when the
     * server selects threads automatically. The route must not block.
     *
     * @return The route
     */
    HttpRouteSpec nonBlocking();
}
