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

import java.util.concurrent.CompletionStage;

/**
 * Locates the target of the rest of a path asynchronously from the body of the request, see
 * {@link LocatorBodySpec#locateAsync(AsyncBodyLocatorHandler, java.util.function.Function)}: an
 * {@link AsyncLocatorHandler} that also receives the body.
 *
 * <p>The locator is called once the body was read, or, for {@link LocatorSpec#body()}, with the
 * body it reads itself, on the thread that completed the read: it must not block. The request
 * is matched again when its stage completes, like for an {@link AsyncLocatorHandler}.</p>
 *
 * @param <B> The type of the body
 * @param <T> The type of the target, which the routes function of the locator receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface AsyncBodyLocatorHandler<B extends @Nullable Object, T> {

    /**
     * Locate the target.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefix, and of the prefixes of the locators
     *                      that located this one; {@link LocatedRoutes#locatedTarget(PathVariables)} is the
     *                      target that owns this locator, if any
     * @param body          The body, see {@link LocatorSpec}
     * @return The stage of the target, which completes with {@code null} if there is none: the
     * request is not found
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable T> locate(HttpRequest<?> request, PathVariables pathVariables, B body) throws Exception;
}
