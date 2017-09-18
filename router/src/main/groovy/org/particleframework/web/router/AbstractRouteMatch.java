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
import org.particleframework.inject.Argument;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.inject.ReturnType;
import org.particleframework.web.router.exceptions.RoutingException;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Abstract implementation of the {@link RouteMatch} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractRouteMatch<T> implements RouteMatch<T> {

    protected final MethodExecutionHandle<T> executableMethod;
    protected final List<Predicate<HttpRequest>> conditions;
    protected final ConversionService<?> conversionService;

    protected AbstractRouteMatch(List<Predicate<HttpRequest>> conditions, MethodExecutionHandle<T> executableMethod, ConversionService<?> conversionService) {
        this.conditions = conditions;
        this.executableMethod = executableMethod;
        this.conversionService = conversionService;
    }

    @Override
    public String getMethodName() {
        return this.executableMethod.getMethodName();
    }

    @Override
    public Class getDeclaringType() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public Argument[] getArguments() {
        return executableMethod.getArguments();
    }

    @Override
    public boolean test(HttpRequest request) {
        for (Predicate<HttpRequest> condition : conditions) {
            if (!condition.test(request)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ReturnType<T> getReturnType() {
        return executableMethod.getReturnType();
    }


    @Override
    public T invoke(Object... arguments) {
        ConversionService conversionService = this.conversionService;

        Argument[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            return executableMethod.invoke();
        } else {
            List argumentList = new ArrayList();
            Map<String, Object> variables = getVariables();
            Iterator<Object> valueIterator = variables.values().iterator();
            int i = 0;
            for (Argument targetArgument : targetArguments) {
                String name = targetArgument.getName();
                Object value = variables.get(name);
                if (value != null) {
                    Optional<?> result = conversionService.convert(value, targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (valueIterator.hasNext()) {
                    Optional<?> result = conversionService.convert(valueIterator.next(), targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (i < arguments.length) {
                    Optional<?> result = conversionService.convert(arguments[i++], targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else {
                    throw new IllegalArgumentException("Wrong number of arguments to method: " + executableMethod);
                }
            }
            return executableMethod.invoke(argumentList.toArray());
        }
    }

    @Override
    public T execute(Map argumentValues) {
        Argument[] targetArguments = getArguments();

        if (targetArguments.length == 0) {
            return executableMethod.invoke();
        } else {
            ConversionService conversionService = this.conversionService;
            Map<String, Object> uriVariables = getVariables();
            List argumentList = new ArrayList();
            for (Argument argument : targetArguments) {
                String name = argument.getName();
                Object value = DefaultRouteBuilder.NO_VALUE;
                if (uriVariables.containsKey(name)) {
                    value = uriVariables.get(name);
                } else if (argumentValues.containsKey(name)) {
                    value = argumentValues.get(name);
                }
                if(value instanceof Supplier) {
                    Supplier supplier = (Supplier) value;
                    Object o = supplier.get();
                    Object fv = value;
                    o = o instanceof Optional ? ((Optional<?>)o).orElseThrow(()-> new RoutingException("Unable to convert value [" + fv + "] for argument: " + argument)) : o;
                    if(o != null) {
                        Optional<?> result = conversionService.convert(o, argument.getType());
                        argumentList.add(result.orElseThrow(() -> new RoutingException("Unable to convert value [" + fv + "] for argument: " + argument)));
                    }
                    else {
                        throw new RoutingException("Required argument [" + argument + "] not specified");
                    }
                }
                else if (value == DefaultRouteBuilder.NO_VALUE) {
                    throw new RoutingException("Required argument [" + argument + "] not specified");
                } else {
                    Object finalValue = value;
                    Optional<?> result = conversionService.convert(finalValue, argument.getType());
                    argumentList.add(result.orElseThrow(() -> new RoutingException("Unable to convert value [" + finalValue + "] for argument: " + argument)));
                }
            }

            return executableMethod.invoke(argumentList.toArray());
        }
    }
}
