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
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

/**
 * Locates the target of the rest of a path from the body of the request, see
 * {@link LocatorBodySpec#locate(BodyLocatorHandler, java.util.function.Function)}: a
 * {@link LocatorHandler} that also receives the body, e.g. the form a JAX-RS resource reads with
 * {@code @FormParam} before its sub-resource locator runs.
 *
 * <p>The locator runs once the body was read, on the thread that completed the read, typically
 * an event loop thread: it must not block. The located routes read the body again, see
 * {@link LocatorBodySpec}. It runs at most once per request, like a {@link LocatorHandler}.</p>
 *
 * @param <B> The type of the body
 * @param <T> The type of the target, which the routes function of the locator receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface BodyLocatorHandler<B extends @Nullable Object, T> {

    /**
     * Locate the target.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefix, and of the prefixes of the locators
     *                      that located this one; {@link LocatedRoutes#locatedTarget(PathVariables)} is the
     *                      target that owns this locator, if any
     * @param body          The body, see {@link LocatorSpec}
     * @return The target, or {@code null} if there is none: the request is not found
     * @throws Exception An error, handled by the error routes like a controller error
     */
    @Nullable T locate(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception;
}
