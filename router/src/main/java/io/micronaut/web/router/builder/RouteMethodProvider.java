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
import io.micronaut.inject.ExecutableMethod;

/**
 * An optional capability of a handler function: the bean method the handler implements, e.g. the
 * method of a resource that a framework integration routes with handler functions. A handler that
 * also implements this interface, besides {@link RequestHandler} or another handler interface,
 * gives its route that method as if it were given with
 * {@link HttpRouteSpec#annotationMetadata(io.micronaut.core.annotation.AnnotationMetadataProvider)}:
 * the route has its annotations, target method, declaring type and method name, and an
 * integration finds it with {@link io.micronaut.web.router.MethodBasedRouteInfo#getAnnotationMetadataProvider()}.
 *
 * <p>The method identifies where the route comes from only. The route still binds the arguments
 * of the handler, calls the handler, and answers with its result, whatever the signature of the
 * method: a synchronous method given to an asynchronous handler does not make the route
 * synchronous.</p>
 *
 * <p>The route reads the method once, when it is added. An element given to the route with
 * {@code annotationMetadata(...)} replaces it, and it replaces the element of the groups of the
 * route. An ordinary lambda gives no method: its origin is not inferred.</p>
 *
 * <pre>{@code
 * final class PayHandler implements RequestHandler, RouteMethodProvider {
 *     public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) { ... }
 *     public ExecutableMethod<?, ?> routeMethod() { return pay; }
 * }
 * routes.POST("/payments/{amount}", new PayHandler());
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteMethodProvider {

    /**
     * @return The bean method the handler implements, never {@code null}
     */
    ExecutableMethod<?, ?> routeMethod();
}
