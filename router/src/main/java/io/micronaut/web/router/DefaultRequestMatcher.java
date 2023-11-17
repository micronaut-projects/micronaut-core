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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.shortcircuit.MatchRule;

import java.util.List;
import java.util.Optional;
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

    public DefaultRequestMatcher(MethodExecutionHandle<T, R> targetMethod,
                                 Argument<?> bodyArgument,
                                 String bodyArgumentName,
                                 List<MediaType> producesMediaTypes,
                                 List<MediaType> consumesMediaTypes,
                                 boolean isPermitsBody,
                                 boolean isErrorRoute,
                                 List<Predicate<HttpRequest<?>>> predicates,
                                 MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(targetMethod, bodyArgument, bodyArgumentName, producesMediaTypes, consumesMediaTypes, isPermitsBody, isErrorRoute, messageBodyHandlerRegistry);
        this.predicates = predicates;
    }

    @Override
    public final boolean matching(HttpRequest<?> httpRequest) {
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

    @Override
    public Optional<MatchRule> matchingRule() {
        if (predicates.stream().allMatch(p -> p instanceof MatchRule)) {
            //noinspection unchecked,rawtypes
            return Optional.of(MatchRule.and((List) predicates));
        } else {
            return Optional.empty();
        }
    }
}
