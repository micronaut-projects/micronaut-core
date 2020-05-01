/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.util.KotlinExecutableMethodUtils;

import java.util.concurrent.CompletionStage;

/**
 * Determines the reactive nature of a route match.
 *
 * @author James Kleeh
 * @since 1.3.5
 */
@Internal
public class ReactiveMetadata {

    private final boolean isFuture;
    private final boolean isReactiveReturnType;
    private final boolean isKotlinSuspendingFunction;
    private final boolean isKotlinFunctionReturnTypeUnit;
    private final boolean isSingle;

    /**
     * @param routeMatch The route match
     */
    public ReactiveMetadata(RouteMatch<?> routeMatch) {
        ReturnType<?> genericReturnType = routeMatch.getReturnType();
        Class<?> javaReturnType = genericReturnType.getType();
        isFuture = CompletionStage.class.isAssignableFrom(javaReturnType);
        isReactiveReturnType = Publishers.isConvertibleToPublisher(javaReturnType) || isFuture;
        if (routeMatch instanceof MethodBasedRouteMatch) {
            MethodBasedRouteMatch<?, ?> methodRouteMatch = (MethodBasedRouteMatch<?, ?>) routeMatch;
            isKotlinSuspendingFunction = methodRouteMatch.getExecutableMethod().isSuspend();
            isKotlinFunctionReturnTypeUnit = isKotlinSuspendingFunction &&
                    KotlinExecutableMethodUtils.isKotlinFunctionReturnTypeUnit(methodRouteMatch.getExecutableMethod());
        } else {
            isKotlinSuspendingFunction = false;
            isKotlinFunctionReturnTypeUnit = false;
        }
        isSingle =
                isReactiveReturnType && Publishers.isSingle(javaReturnType) ||
                        isResponsePublisher(genericReturnType, javaReturnType) ||
                        isFuture ||
                        routeMatch.getAnnotationMetadata().booleanValue(Produces.class, "single").orElse(false) ||
                        isKotlinSuspendingFunction;
    }

    private boolean isResponsePublisher(ReturnType<?> genericReturnType, Class<?> javaReturnType) {
        return Publishers.isConvertibleToPublisher(javaReturnType) && genericReturnType.getFirstTypeVariable().map(arg -> HttpResponse.class.isAssignableFrom(arg.getType())).orElse(false);
    }

    /**
     * @return True if the route returns a future
     */
    public boolean isFuture() {
        return isFuture;
    }

    /**
     * @return True if the route returns a reactive type
     */
    public boolean isReactiveReturnType() {
        return isReactiveReturnType;
    }

    /**
     * @return True if the route method is a kotlin suspend function
     */
    public boolean isKotlinSuspendingFunction() {
        return isKotlinSuspendingFunction;
    }

    /**
     * @return True if the route method returns a kotlin unit
     */
    public boolean isKotlinFunctionReturnTypeUnit() {
        return isKotlinFunctionReturnTypeUnit;
    }

    /**
     * @return True if the route method returns a single
     */
    public boolean isSingle() {
        return isSingle;
    }
}
