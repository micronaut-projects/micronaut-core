/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router;

import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpStatus;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.MethodExecutionHandle;

import java.lang.reflect.AnnotatedElement;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A {@link RouteMatch} for a status code
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class StatusRouteMatch<T> extends AbstractRouteMatch<T> {

    final HttpStatus httpStatus;

    StatusRouteMatch(HttpStatus httpStatus, DefaultRouteBuilder.AbstractRoute abstractRoute, ConversionService<?> conversionService) {
        super(abstractRoute, conversionService);
        this.httpStatus = httpStatus;
    }

    @Override
    public Map<String, Object> getVariables() {
        return Collections.emptyMap();
    }

    @Override
    protected RouteMatch<T> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments) {
        return new StatusRouteMatch<T>(httpStatus, abstractRoute, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return Collections.unmodifiableCollection(requiredArguments);
            }

            @Override
            public Map<String, Object> getVariables() {
                return newVariables;
            }
        };
    }

    @Override
    public RouteMatch<T> decorate(Function<RouteMatch<T>, T> executor) {
        Map<String, Object> variables = getVariables();
        Collection<Argument> arguments = getRequiredArguments();
        RouteMatch thisRoute = this;
        return new StatusRouteMatch<T>(httpStatus, abstractRoute, conversionService) {
            @Override
            public Collection<Argument> getRequiredArguments() {
                return Collections.unmodifiableCollection(arguments);
            }

            @Override
            public T execute(Map argumentValues) {
                return (T) executor.apply(thisRoute);
            }

            @Override
            public Map<String, Object> getVariables() {
                return variables;
            }
        };
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return executableMethod.getAnnotatedElements();
    }
}
