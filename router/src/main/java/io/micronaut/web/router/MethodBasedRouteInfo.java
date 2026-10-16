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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.AsyncRequestBody;
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
     * @return The element, or empty for a route to a handler function that was given no element,
     * by itself or by a group, even one with annotations given with {@code annotate(...)}
     * @since 5.3.0
     */
    @Experimental
    default Optional<AnnotationMetadataProvider> getAnnotationMetadataProvider() {
        MethodExecutionHandle<T, R> targetMethod = getTargetMethod();
        if (targetMethod instanceof HandlerMethod<?> handlerMethod) {
            return Optional.ofNullable(handlerMethod.getAnnotationMetadataProvider());
        }
        return Optional.of(targetMethod.getExecutableMethod());
    }

    /**
     * Whether the route takes the request body as an {@link AsyncRequestBody}: a handler route
     * that declares its body with {@link io.micronaut.web.router.builder.HttpRouteSpec#body()}, or a
     * controller method with an {@link AsyncRequestBody} argument. Such a route
     * reads the body itself, so a runtime does not decode it into the request. The routes of the
     * router compute it once, when the route is built.
     *
     * @return Whether an argument of the target method is an {@link AsyncRequestBody}
     * @since 5.3.0
     */
    default boolean isAsyncRequestBody() {
        for (Argument<?> argument : getTargetMethod().getArguments()) {
            if (AsyncRequestBody.class.isAssignableFrom(argument.getType())) {
                return true;
            }
        }
        return false;
    }

}
