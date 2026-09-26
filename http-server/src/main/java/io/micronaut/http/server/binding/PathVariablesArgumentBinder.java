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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.filter.FilterArgumentBinderPredicate;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RoutePathVariables;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Binds the {@link PathVariables} argument of a controller method, or of a filter method of a
 * server filter that runs after the request is routed, to the variables of the route the request
 * matched. It is unsatisfied before the request is routed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class PathVariablesArgumentBinder implements TypedRequestArgumentBinder<PathVariables>, FilterArgumentBinderPredicate {
    private static final Argument<PathVariables> ARGUMENT = Argument.of(PathVariables.class);

    private final ConversionService conversionService;

    PathVariablesArgumentBinder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Argument<PathVariables> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<PathVariables> bind(ArgumentConversionContext<PathVariables> context, HttpRequest<?> source) {
        Optional<RouteMatch<?>> match = RouteAttributes.getRouteMatch(source);
        if (match.isPresent() && match.get() instanceof UriRouteMatch<?, ?> uriMatch) {
            PathVariables pathVariables = new RoutePathVariables(uriMatch.getVariableValues(), conversionService);
            return () -> Optional.of(pathVariables);
        }
        return BindingResult.unsatisfied();
    }

    @Override
    public boolean test(Argument<?> argument, MutablePropagatedContext mutablePropagatedContext, HttpRequest<?> request,
                        @Nullable HttpResponse<?> response, @Nullable Throwable failure) {
        return argument.isNullable() || RouteAttributes.getRouteMatch(request).filter(UriRouteMatch.class::isInstance).isPresent();
    }
}
