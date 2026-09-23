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
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * Locates the target of the rest of a path, for
 * {@link HttpRouteBuilder#locate(String, LocatorHandler, java.util.function.Function)}: like a
 * JAX-RS sub-resource locator, it returns the object whose routes match the rest of the path.
 *
 * <p>The router runs the locator while it matches the request, on the thread that matches it,
 * typically an event loop thread, before the body is read: it must not block, and it receives
 * the request and the path variables of the prefix only. It may run more than once for a
 * request, e.g. again to find the methods allowed for the path when no route matched.</p>
 *
 * @param <T> The type of the target, which the route table function of the locator receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface LocatorHandler<T> {

    /**
     * Locate the target.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefix, and of the prefixes of the locators
     *                      that located this one; {@link PathVariables#locatedTarget()} is the
     *                      target that owns this locator, if any
     * @return The target, or {@code null} if there is none: the request is not found
     * @throws Exception An error, handled by the error routes like a controller error
     */
    @Nullable T locate(HttpRequest<?> request, PathVariables pathVariables) throws Exception;
}
