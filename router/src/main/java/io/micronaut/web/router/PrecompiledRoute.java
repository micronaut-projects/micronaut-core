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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * A URI route derived at compile time, see
 * {@link io.micronaut.web.router.annotation.PrecompiledHttpRoutes}. Types are referenced by name,
 * so that the generated class does not need access to them.
 *
 * @param controllerType      The name of the controller type
 * @param methodName          The name of the controller method
 * @param argumentTypes       The names of the erased argument types of the method
 * @param httpMethodName      The HTTP method name
 * @param httpMethod          The name of the {@link io.micronaut.http.HttpMethod}
 * @param uri                 The URI template
 * @param consumes            The consumed media types, or {@code null} for the route builder default
 * @param produces            The produced media types, or {@code null} for the route builder default
 * @param implicitHead        Whether this is the implicit {@code HEAD} route of a {@code GET} method
 * @param port                The port of the route, or {@code -1}
 * @param declaringTypeTarget Whether the route targets the method through its declaring type
 * @param requiredPathPrefix  See {@link IndexedRoute#getRequiredPathPrefix()}
 * @param rawLength           See {@link IndexedRoute#getRawLength()}
 * @param pathVariableCount   See {@link IndexedRoute#getPathVariableCount()}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public record PrecompiledRoute(String controllerType,
                               String methodName,
                               String[] argumentTypes,
                               String httpMethodName,
                               String httpMethod,
                               String uri,
                               String @Nullable [] consumes,
                               String @Nullable [] produces,
                               boolean implicitHead,
                               int port,
                               boolean declaringTypeTarget,
                               String requiredPathPrefix,
                               int rawLength,
                               int pathVariableCount) {
}
