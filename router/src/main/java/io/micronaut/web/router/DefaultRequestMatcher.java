/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.RequestConditions;

import java.util.List;
import java.util.function.Predicate;

/**
 * The default {@link RequestMatcher} implementation.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public sealed class DefaultRequestMatcher<T, R> extends DefaultMethodBasedRouteInfo<T, R> implements RequestMatcher
    permits DefaultErrorRouteInfo, DefaultStatusRouteInfo, DefaultUrlRouteInfo {

    private final List<Predicate<HttpRequest<?>>> predicates;
    /**
     * The body conditions among the declarative conditions of the route, empty for most routes.
     */
    private final List<RequestCondition.Body<?>> bodyConditions;

    public DefaultRequestMatcher(MethodExecutionHandle<T, R> targetMethod,
                                 @Nullable Argument<?> bodyArgument,
                                 @Nullable String bodyArgumentName,
                                 List<MediaType> producesMediaTypes,
                                 List<MediaType> consumesMediaTypes,
                                 boolean isPermitsBody,
                                 boolean isErrorRoute,
                                 List<Predicate<HttpRequest<?>>> predicates,
                                 MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(targetMethod, bodyArgument, bodyArgumentName, producesMediaTypes, consumesMediaTypes, isPermitsBody, isErrorRoute, messageBodyHandlerRegistry);
        this.predicates = predicates;
        this.bodyConditions = bodyConditions(predicates);
    }

    @Override
    public boolean matching(HttpRequest<?> httpRequest) {
        if (predicates.isEmpty()) {
            return true;
        }
        for (Predicate<HttpRequest<?>> predicate : predicates) {
            if (!predicate.test(httpRequest)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return The declarative conditions of the route, normalized, in the order they are evaluated
     * @see UriRouteInfo#getConditions()
     */
    public List<RequestCondition> getConditions() {
        for (Predicate<HttpRequest<?>> predicate : predicates) {
            if (predicate instanceof RequestConditions.RoutePredicate routePredicate) {
                return RequestConditions.conjuncts(routePredicate.condition());
            }
        }
        return List.of();
    }

    private static List<RequestCondition.Body<?>> bodyConditions(List<Predicate<HttpRequest<?>>> predicates) {
        for (Predicate<HttpRequest<?>> predicate : predicates) {
            if (predicate instanceof RequestConditions.RoutePredicate routePredicate) {
                return RequestConditions.bodyConditions(routePredicate.condition());
            }
        }
        return List.of();
    }

    /**
     * @return The body conditions of the route, see {@link UriRouteInfo#getBodyConditions()}
     */
    public List<RequestCondition.Body<?>> getBodyConditions() {
        return bodyConditions;
    }
}
