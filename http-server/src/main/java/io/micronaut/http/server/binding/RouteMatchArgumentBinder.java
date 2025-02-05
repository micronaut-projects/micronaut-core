/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.filter.FilterArgumentBinderPredicate;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Argument binder for {@link RouteMatch} objects.
 *
 * @since 4.6.0
 * @author Jonas Konrad
 */
@Singleton
@Internal
final class RouteMatchArgumentBinder implements TypedRequestArgumentBinder<RouteMatch<?>>, FilterArgumentBinderPredicate {
    RouteMatchArgumentBinder() {
    }

    @Override
    public Argument<RouteMatch<?>> argumentType() {
        return (Argument) Argument.of(RouteMatch.class);
    }

    @Override
    public BindingResult<RouteMatch<?>> bind(ArgumentConversionContext<RouteMatch<?>> context, HttpRequest<?> source) {
        Optional<RouteMatch<?>> match = RouteAttributes.getRouteMatch(source);
        return () -> match;
    }

    @Override
    public boolean test(Argument<?> argument, MutablePropagatedContext mutablePropagatedContext, HttpRequest<?> request, @Nullable HttpResponse<?> response, @Nullable Throwable failure) {
        return argument.isNullable() || RouteAttributes.getRouteMatch(request).isPresent();
    }
}
