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
 * An asynchronous locator of the routes of a located target that receives the target,
 * which owns the locator, see {@link LocatedHttpRouteBuilder}: otherwise the same as an
 * {@link AsyncLocatorHandler}.
 *
 * @param <T> The type of the located target that owns the locator
 * @param <U> The type of the target the locator locates
 * @author Denis Stepanov
 * @since 5.3.0
 * @see LocatedHttpRouteBuilder#locateAsync(String, LocatedAsyncLocatorHandler, java.util.function.Function)
 */
@Experimental
@FunctionalInterface
public interface LocatedAsyncLocatorHandler<T, U> {

    /**
     * Locate the target.
     *
     * @param request       The request
     * @param pathVariables The path variables of the prefix, and of the prefixes of the locators
     *                      that located this one
     * @param target        The located target that owns the locator
     * @return The stage of the target, which completes with {@code null} if there is none: the
     * request is not found
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable U> locate(HttpRequest<?> request, PathVariables pathVariables, T target) throws Exception;
}
