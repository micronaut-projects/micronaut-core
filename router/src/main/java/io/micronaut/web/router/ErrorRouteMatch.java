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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a match for an error.
 *
 * @param <T> The target type
 * @param <R> The return type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class ErrorRouteMatch<T, R> extends AbstractRouteMatch<T, R> {

    private final Throwable error;
    private final Map<String, Object> variables;

    /**
     * @param error The throwable
     * @param abstractRoute The abstract route
     * @param conversionService The conversion service
     */
    ErrorRouteMatch(Throwable error, DefaultRouteBuilder.AbstractRoute abstractRoute, ConversionService<?> conversionService) {
        super(abstractRoute, conversionService);
        this.error = error;
        this.variables = new LinkedHashMap<>();
        for (Argument argument : getArguments()) {
            if (argument.getType().isInstance(error)) {
                variables.put(argument.getName(), error);
            }
        }
    }

    @Override
    public Collection<Argument> getRequiredArguments() {
        return Arrays
            .stream(getArguments())
            .filter(argument -> !argument.getType().isInstance(error))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getVariableValues() {
        return variables;
    }

    @Override
    public boolean isErrorRoute() {
        return true;
    }

    @Override
    protected RouteMatch<R> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments) {
        return new ErrorRouteMatch<T, R>(error, abstractRoute, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return requiredArguments;
            }

            @Override
            public Map<String, Object> getVariableValues() {
                return newVariables;
            }
        };
    }

    @Override
    public RouteMatch<R> decorate(Function<RouteMatch<R>, R> executor) {
        Map<String, Object> variables = getVariableValues();
        Collection<Argument> arguments = getRequiredArguments();
        RouteMatch thisRoute = this;
        return new ErrorRouteMatch(error, abstractRoute, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return arguments;
            }

            @Override
            public T execute(Map argumentValues) {
                return (T) executor.apply(thisRoute);
            }

            @Override
            public Map<String, Object> getVariableValues() {
                return variables;
            }
        };
    }

    @Override
    public String toString() {
        return abstractRoute.toString();
    }
}
