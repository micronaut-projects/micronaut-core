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
package io.micronaut.web.router;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.builder.HandlerMethod;

import java.util.Optional;

/**
 * Represents a route that is backed by a method.
 *
 * @param <T> The target
 * @param <R> The result
 * @author James Kleeh
 * @since 1.0
 */
public interface MethodBasedRouteInfo<T, R> extends RouteInfo<R> {

    /**
     * @return The {@link MethodExecutionHandle}
     */
    MethodExecutionHandle<T, R> getTargetMethod();

    String[] getArgumentNames();

    RequestArgumentBinder<Object>[] resolveArgumentBinders(RequestBinderRegistry requestBinderRegistry);

    /**
     * The element the route has the annotations of: the {@link ExecutableMethod} of a route to a
     * bean method, and, for a route to a handler function, the element given to it with
     * {@link io.micronaut.web.router.builder.HttpRouteSpec#annotationMetadata(AnnotationMetadataProvider)}.
     * An integration that routes the methods of its own resources with handler functions finds
     * the bean method of the matched route with {@code instanceof ExecutableMethod}.
     *
     * @return The element, or empty for a route to a handler function that was given no annotations
     * @since 5.3.0
     */
    default Optional<AnnotationMetadataProvider> getAnnotationMetadataProvider() {
        MethodExecutionHandle<T, R> targetMethod = getTargetMethod();
        if (targetMethod instanceof HandlerMethod<?> handlerMethod) {
            return Optional.ofNullable(handlerMethod.getAnnotationMetadataProvider());
        }
        return Optional.of(targetMethod.getExecutableMethod());
    }

}
