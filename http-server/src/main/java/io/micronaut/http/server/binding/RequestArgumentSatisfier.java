/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.BodyArgumentBinder;
import io.micronaut.http.bind.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.web.router.NullArgument;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.UnresolvedArgument;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A class containing methods to aid in satisfying arguments of a {@link io.micronaut.web.router.Route}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
public class RequestArgumentSatisfier {

    private final RequestBinderRegistry binderRegistry;

    /**
     * @param requestBinderRegistry The Request binder registry
     */
    public RequestArgumentSatisfier(RequestBinderRegistry requestBinderRegistry) {
        this.binderRegistry = requestBinderRegistry;
    }

    /**
     * @return The request binder registry
     */
    public RequestBinderRegistry getBinderRegistry() {
        return binderRegistry;
    }

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param route            The route
     * @param request          The request
     * @param satisfyOptionals Whether to satisfy optionals
     * @return The route
     */
    public RouteMatch<?> fulfillArgumentRequirements(RouteMatch<?> route, HttpRequest<?> request, boolean satisfyOptionals) {
        Collection<Argument> requiredArguments = route.getRequiredArguments();
        Map<String, Object> argumentValues;

        if (requiredArguments.isEmpty()) {
            // no required arguments so just execute
            argumentValues = Collections.emptyMap();
        } else {
            argumentValues = new LinkedHashMap<>();
            // Begin try fulfilling the argument requirements
            for (Argument argument : requiredArguments) {
                getValueForArgument(argument, request, satisfyOptionals).ifPresent((value) ->
                    argumentValues.put(argument.getName(), value));
            }
        }

        route = route.fulfill(argumentValues);
        return route;
    }

    /**
     * @param argument         The argument
     * @param request          The HTTP request
     * @param satisfyOptionals Whether to satisfy optionals
     * @return An {@link Optional} for the value
     */
    protected Optional<Object> getValueForArgument(Argument argument, HttpRequest<?> request, boolean satisfyOptionals) {
        Object value = null;
        Optional<ArgumentBinder> registeredBinder =
            binderRegistry.findArgumentBinder(argument, request);
        if (registeredBinder.isPresent()) {
            ArgumentBinder argumentBinder = registeredBinder.get();
            ArgumentConversionContext conversionContext = ConversionContext.of(
                argument,
                request.getLocale().orElse(null),
                request.getCharacterEncoding()
            );

            if (argumentBinder instanceof BodyArgumentBinder) {
                if (argumentBinder instanceof NonBlockingBodyArgumentBinder) {
                    ArgumentBinder.BindingResult bindingResult = argumentBinder
                        .bind(conversionContext, request);

                    if (bindingResult.isPresentAndSatisfied()) {
                        value = bindingResult.get();
                    }

                } else {
                    value = getValueForBlockingBodyArgumentBinder(request, argumentBinder, conversionContext);
                }
            } else {
                ArgumentBinder.BindingResult bindingResult = argumentBinder
                    .bind(conversionContext, request);

                if (argument.getType() == Optional.class) {
                    if (bindingResult.isSatisfied() || satisfyOptionals) {
                        Optional optionalValue = bindingResult.getValue();
                        if (optionalValue.isPresent()) {
                            value = optionalValue.get();
                        } else {
                            value = optionalValue;
                        }
                    }
                } else if (bindingResult.isPresentAndSatisfied()) {
                    value = bindingResult.get();
                } else if (bindingResult.isSatisfied() && argument.isNullable()) {
                    value = NullArgument.INSTANCE;
                } else if (HttpMethod.requiresRequestBody(request.getMethod()) || argument.isNullable()) {
                    value = (UnresolvedArgument) () -> {
                        ArgumentBinder.BindingResult result = argumentBinder.bind(conversionContext, request);
                        Optional<ConversionError> lastError = conversionContext.getLastError();
                        if (lastError.isPresent()) {
                            return (ArgumentBinder.BindingResult) () -> lastError;
                        }
                        return result;
                    };
                }
            }
        }
        return Optional.ofNullable(value);
    }

    /**
     *
     * @param request The request
     * @param argumentBinder The argument binder
     * @param conversionContext The conversion context
     * @return The body argument
     */
    private Object getValueForBlockingBodyArgumentBinder(HttpRequest<?> request, ArgumentBinder argumentBinder, ArgumentConversionContext conversionContext) {
        return (UnresolvedArgument) () -> argumentBinder.bind(conversionContext, request);
    }
}
