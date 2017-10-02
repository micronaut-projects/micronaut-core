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

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents a match for an error
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ErrorRouteMatch<T> extends StatusRouteMatch<T> {
    private final Throwable error;
    private final Map<String, Object> variables;

    public ErrorRouteMatch(Throwable error, MethodExecutionHandle executableMethod, List<Predicate<HttpRequest>> conditions, ConversionService<?> conversionService) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, executableMethod, conditions, conversionService);
        this.error = error;
        this.variables = new LinkedHashMap<>();
        for (Argument argument : getArguments()) {
            if(argument.getType().isInstance(error)) {
                variables.put(argument.getName(), error);
            }
        }
    }

    @Override
    public Collection<Argument> getRequiredArguments() {
        return Arrays.stream(getArguments())
                     .filter(argument -> !argument.getType().isInstance(error))
                     .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }
}
