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
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpResponse;
import io.micronaut.inject.util.KotlinExecutableMethodUtils;

/**
 * Common information shared between route and route match.
 *
 * @param <R> The route
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteInfo<R> extends AnnotationMetadataProvider {
    /**
     * @return The return type
     */
    ReturnType<? extends R> getReturnType();

    /**
     * @return Is this route match a suspended function (Kotlin).
     * @since 2.0.0
     */
    default boolean isSuspended() {
        return getReturnType().isSuspended();
    }

    /**
     * @return Is the route a reactive route.
     * @since 2.0.0
     */
    default boolean isReactive() {
        return getReturnType().isReactive();
    }

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    default boolean isSingleResult() {
        ReturnType<? extends R> returnType = getReturnType();
        return returnType.isSingleResult() ||
                (isReactive() && returnType.getFirstTypeVariable()
                        .filter(t -> HttpResponse.class.isAssignableFrom(t.getType())).isPresent()) ||
                returnType.isSuspended();
    }

    /**
     * @return Does the route emit a single result or multiple results
     * @since 2.0
     */
    default boolean isSpecifiedSingle() {
        return getReturnType().isSpecifiedSingle();
    }

    /**
     * @return is the return type completable
     * @since 2.0
     */
    default boolean isCompletable() {
        return getReturnType().isCompletable();
    }

    /**
     * @return Is the route an async route.
     * @since 2.0.0
     */
    default boolean isAsync() {
        return getReturnType().isAsync();
    }

    /**
     * @return Is the route an async or reactive route.
     * @since 2.0.0
     */
    default boolean isAsyncOrReactive() {
        return getReturnType().isAsyncOrReactive();
    }

    /**
     * @return Does the route return void
     * @since 2.0.0
     */
    default boolean isVoid() {
        if (getReturnType().isVoid()) {
            return true;
        } else if (this instanceof MethodBasedRouteMatch && isSuspended()) {
            return KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit(((MethodBasedRouteMatch) this).getExecutableMethod());
        }
        return false;
    }
}
