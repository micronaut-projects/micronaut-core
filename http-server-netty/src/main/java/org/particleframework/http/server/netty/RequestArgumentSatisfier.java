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
package org.particleframework.http.server.netty;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.server.binding.RequestBinderRegistry;
import org.particleframework.http.server.binding.binders.BodyArgumentBinder;
import org.particleframework.http.server.binding.binders.NonBlockingBodyArgumentBinder;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.UnresolvedArgument;

import java.util.*;

/**
 * A class containing methods to aid in satisfying arguments of a {@link org.particleframework.web.router.Route}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class RequestArgumentSatisfier {
    private final RequestBinderRegistry binderRegistry;

    RequestArgumentSatisfier(RequestBinderRegistry requestBinderRegistry) {
        this.binderRegistry = requestBinderRegistry;
    }

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request
     *
     * @param route The route
     * @param request The request
     * @return The route
     */
    RouteMatch<Object> fulfillArgumentRequirements(RouteMatch<Object> route, HttpRequest<?> request) {
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
                            Optional bindingResult = argumentBinder
                                    .bind(conversionContext, request);

                            if (bindingResult.isPresent()) {
                                argumentValues.put(argumentName, bindingResult.get());
                            }

                        } else {
                            argumentValues.put(argumentName, (UnresolvedArgument) () ->
                                    argumentBinder.bind(conversionContext, request)
                            );
                            ((NettyHttpRequest)request).setBodyRequired(true);
                        }
                    } else {

                        Optional bindingResult = argumentBinder
                                .bind(conversionContext, request);
                        if (argument.getType() == Optional.class) {
                            argumentValues.put(argumentName, bindingResult);
                        } else if (bindingResult.isPresent()) {
                            argumentValues.put(argumentName, bindingResult.get());
                        } else if (HttpMethod.requiresRequestBody(request.getMethod())) {
                            argumentValues.put(argumentName, (UnresolvedArgument) () -> {
                                Optional result = argumentBinder.bind(conversionContext, request);
                                Optional<ConversionError> lastError = conversionContext.getLastError();
                                if (lastError.isPresent()) {
                                    return lastError;
                                }
                                return result;
                            });
                        }
                    }
                }
            }
        }

        route = route.fulfill(argumentValues);
        return route;
    }
}
