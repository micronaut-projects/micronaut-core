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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.PathVariables;
import io.micronaut.http.bind.MatchedPathVariables;
import io.micronaut.web.router.exceptions.UnsatisfiedPathVariableRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;

import java.util.Map;

/**
 * The {@link PathVariables} of a route match, which fail like the path variable arguments of a
 * controller method: a missing variable with an {@link UnsatisfiedPathVariableRouteException}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public class RoutePathVariables extends MatchedPathVariables {

    /**
     * @param values            The variable values, viewed read-only
     * @param conversionService The conversion service of the route
     */
    public RoutePathVariables(Map<String, Object> values, ConversionService conversionService) {
        super(values, conversionService);
    }

    @Override
    protected RuntimeException unsatisfied(String name, Argument<?> argument) {
        return new UnsatisfiedPathVariableRouteException(name, argument);
    }

    @Override
    protected RuntimeException unconvertible(Argument<?> argument) {
        return UnsatisfiedRouteException.create(argument);
    }
}
