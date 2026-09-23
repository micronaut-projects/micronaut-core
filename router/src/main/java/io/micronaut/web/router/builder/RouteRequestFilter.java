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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMessage;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * A filter of one route's requests, declared with {@link io.micronaut.web.router.builder.HttpRouteSpec#before(RouteRequestFilter)}.
 * Like a {@code @RequestFilter} method it runs before the route, after the application's
 * filters, and can answer the request instead of the route.
 *
 * <p>Like a {@code @RequestFilter} method with a {@link MutableHttpRequest} parameter, it can
 * change the request for what runs after it: the next filters, the matching of the route for a
 * {@link ServerFilterSpec#preMatching() pre-matching} filter, and the route. The request it is
 * given is the request itself if it is mutable, and its {@link io.micronaut.http.HttpRequest#mutate()
 * mutable view} otherwise, which is a {@link io.micronaut.http.ServerHttpRequest} if the request is
 * one, so the filter can read the bytes of the body.</p>
 * <ul>
 *     <li>A header or an attribute it changes is changed in the request: the view shares them.</li>
 *     <li>A URI it changes replaces the request with the mutable request, like a {@code void}
 *     filter method that changed the URI of its mutable request: e.g. a pre-matching filter that
 *     rewrites the path changes the route that is matched.</li>
 *     <li>The parameters and the body object of the view, see {@link MutableHttpRequest#getParameters()}
 *     and {@link MutableHttpRequest#body(Object)}, are its own: a change to them is lost unless the
 *     filter continues with the view, by returning it or by changing its URI.</li>
 *     <li>To change anything else, e.g. the method or the bytes of the body, it returns the request
 *     to continue with, like a filter method returning a request. A request that wraps the request
 *     it was given, e.g. an {@link io.micronaut.http.HttpRequestWrapper} with another method, keeps
 *     its body. A request with another body is a {@link io.micronaut.http.ServerHttpRequest} whose
 *     {@link io.micronaut.http.ServerHttpRequest#byteBody() byteBody()} is the new body: the body
 *     binders of the route, e.g. of a {@code @Body} parameter or of a {@link BodyRequestHandler},
 *     and {@link io.micronaut.http.AsyncServerHttpRequest} read it.</li>
 * </ul>
 *
 * <pre>{@code
 * routes.filter("/**").preMatching().before(request -> {
 *     String override = request.getHeaders().get("X-HTTP-Method-Override");
 *     if (override == null) {
 *         return null;
 *     }
 *     return new HttpRequestWrapper<>(request) {
 *         @Override
 *         public HttpMethod getMethod() {
 *             return HttpMethod.parse(override);
 *         }
 *
 *         @Override
 *         public String getMethodName() {
 *             return override;
 *         }
 *     };
 * });
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface RouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request The request, to change in place for what runs after the filter
     * @return A response to answer the request with instead of the route, a request to continue
     * with instead of the request, or {@code null} to proceed with the request
     * @throws Exception An error, handled by the error routes
     */
    @Nullable HttpMessage<?> filter(MutableHttpRequest<?> request) throws Exception;
}
