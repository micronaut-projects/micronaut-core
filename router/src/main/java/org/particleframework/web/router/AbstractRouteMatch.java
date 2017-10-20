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

import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.HttpRequest;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.core.type.ReturnType;
import org.particleframework.web.router.exceptions.RoutingException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Abstract implementation of the {@link RouteMatch} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractRouteMatch<R> implements RouteMatch<R> {

    protected final MethodExecutionHandle<R> executableMethod;
    protected final List<Predicate<HttpRequest>> conditions;
    protected final ConversionService<?> conversionService;

    protected AbstractRouteMatch(List<Predicate<HttpRequest>> conditions, MethodExecutionHandle<R> executableMethod, ConversionService<?> conversionService) {
        this.conditions = conditions;
        this.executableMethod = executableMethod;
        this.conversionService = conversionService;

    }

    @Override
    public Method getTargetMethod() {
        return executableMethod.getTargetMethod();
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
    public ReturnType<R> getReturnType() {
        return executableMethod.getReturnType();
    }


    @Override
    public R invoke(Object... arguments) {
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
    public R execute(Map<String, Object> argumentValues) {
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

                    if(o instanceof Optional) {
                        o = ((Optional<?>)o).orElseThrow(()-> new RoutingException("Required argument [" + argument + "] not specified"));
                    }
                    if(o != null) {
                        if(o instanceof ConversionError) {
                            ConversionError conversionError = (ConversionError) o;
                            Exception cause = conversionError.getCause();
                            throw new RoutingException("Required argument [" + argument + "] is invalid: " + cause.getMessage(), cause);
                        }
                        else {
                            ConversionContext conversionContext = ConversionContext.of(argument);
                            Optional<?> result = conversionService.convert(o, argument.getType(), conversionContext);
                            argumentList.add(resolveValueOrError(argument, conversionContext, result, value));
                        }
                    }
                    else {
                        throw new RoutingException("Required argument [" + argument + "] not specified");
                    }
                }
                else if (value == DefaultRouteBuilder.NO_VALUE) {
                    throw new RoutingException("Required argument [" + argument + "] not specified");
                } else {
                    ConversionContext conversionContext = ConversionContext.of(argument);
                    Optional<?> result = conversionService.convert(value, argument.getType(), conversionContext);
                    argumentList.add(resolveValueOrError(argument, conversionContext, result, value));
                }
            }

            return executableMethod.invoke(argumentList.toArray());
        }
    }

    protected Object resolveValueOrError(Argument argument, ConversionContext conversionContext, Optional<?> result, Object originalValue) {
        return result.orElseThrow(() -> {
            RoutingException routingException;
            Optional<ConversionError> lastError = conversionContext.getLastError();
            routingException = lastError.map(conversionError -> new RoutingException("Unable to convert value [" + originalValue + "] for argument: " + argument, conversionError.getCause()))
                                        .orElseGet(() -> new RoutingException("Unable to convert value [" + originalValue + "] for argument: " + argument));
            return routingException;
        });
    }
}
