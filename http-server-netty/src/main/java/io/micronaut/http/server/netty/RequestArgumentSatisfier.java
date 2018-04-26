/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.binding.RequestBinderRegistry;
import io.micronaut.http.server.binding.binders.BodyArgumentBinder;
import io.micronaut.http.server.binding.binders.NonBlockingBodyArgumentBinder;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.UnresolvedArgument;

import javax.annotation.Nullable;
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
@Internal
class RequestArgumentSatisfier {

    private final RequestBinderRegistry binderRegistry;

    /**
     * @param requestBinderRegistry The Request binder registry
     */
    RequestArgumentSatisfier(RequestBinderRegistry requestBinderRegistry) {
        this.binderRegistry = requestBinderRegistry;
    }

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param route            The route
     * @param request          The request
     * @param satisfyOptionals Whether to satisfy optionals
     * @return The route
     */
    RouteMatch<?> fulfillArgumentRequirements(RouteMatch<?> route, HttpRequest<?> request, boolean satisfyOptionals) {
        Collection<Argument> requiredArguments = route.getRequiredArguments();
        Map<String, Object> argumentValues;

        if (requiredArguments.isEmpty()) {
            // no required arguments so just execute
            argumentValues = Collections.emptyMap();
        } else {
            argumentValues = new LinkedHashMap<>();
            // Begin try fulfilling the argument requirements
            for (Argument argument : requiredArguments) {
                Optional<ArgumentBinder> registeredBinder =
                    binderRegistry.findArgumentBinder(argument, request);
                if (registeredBinder.isPresent()) {
                    ArgumentBinder argumentBinder = registeredBinder.get();
                    String argumentName = argument.getName();
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
                                argumentValues.put(argumentName, bindingResult.get());
                            }

                        } else {
                            argumentValues.put(argumentName, (UnresolvedArgument) () ->
                                argumentBinder.bind(conversionContext, request)
                            );
                            ((NettyHttpRequest) request).setBodyRequired(true);
                        }
                    } else {

                        ArgumentBinder.BindingResult bindingResult = argumentBinder
                            .bind(conversionContext, request);
                        if (argument.getType() == Optional.class) {
                            if (bindingResult.isSatisfied() || satisfyOptionals) {
                                Optional value = bindingResult.getValue();
                                if (value.isPresent()) {
                                    argumentValues.put(argumentName, value.get());
                                } else {
                                    argumentValues.put(argumentName, value);
                                }
                            }
                        } else if (bindingResult.isPresentAndSatisfied()) {
                            argumentValues.put(argumentName, bindingResult.get());
                        } else if (HttpMethod.requiresRequestBody(request.getMethod())) {
                            argumentValues.put(argumentName, (UnresolvedArgument) () -> {
                                ArgumentBinder.BindingResult result = argumentBinder.bind(conversionContext, request);
                                Optional<ConversionError> lastError = conversionContext.getLastError();
                                if (lastError.isPresent()) {
                                    return (ArgumentBinder.BindingResult) () -> lastError;
                                }
                                return result;
                            });
                        } else if (argument.getDeclaredAnnotation(Nullable.class) != null) {
                            argumentValues.put(argumentName, (UnresolvedArgument) () -> ArgumentBinder.BindingResult.EMPTY);
                        }
                    }
                }
            }
        }

        route = route.fulfill(argumentValues);
        return route;
    }
}
