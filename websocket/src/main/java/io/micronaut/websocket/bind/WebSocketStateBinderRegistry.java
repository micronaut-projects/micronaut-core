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
package io.micronaut.websocket.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.QueryValueArgumentBinder;
import io.micronaut.http.bind.binders.UnmatchedRequestArgumentBinder;
import io.micronaut.websocket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles binding WebSocket arguments from {@link WebSocketState}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class WebSocketStateBinderRegistry implements ArgumentBinderRegistry<WebSocketState> {

    private final ArgumentBinderRegistry<HttpRequest<?>> requestBinderRegistry;

    private final Map<Class<?>, ArgumentBinder<?, WebSocketState>> byType = new HashMap<>(5);
    private final ArgumentBinder<Object, HttpRequest<?>> queryValueArgumentBinder;

    /**
     * Default constructor.
     *
     * @param requestBinderRegistry The request binder registry
     * @param conversionService The conversionService
     */
    public WebSocketStateBinderRegistry(RequestBinderRegistry requestBinderRegistry, ConversionService conversionService) {
        this.requestBinderRegistry = requestBinderRegistry;
        ArgumentBinder<Object, WebSocketState> sessionBinder = (context, source) -> () -> Optional.of(source.getSession());
        this.byType.put(WebSocketSession.class, sessionBinder);
        this.queryValueArgumentBinder = new QueryValueArgumentBinder<>(conversionService);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, WebSocketState>> findArgumentBinder(Argument<T> argument) {
        Optional<ArgumentBinder<T, HttpRequest<?>>> argumentBinder = requestBinderRegistry.findArgumentBinder(argument);
        if (argumentBinder.isPresent()) {
            ArgumentBinder<T, HttpRequest<?>> adapted = argumentBinder.get();

            boolean isUnmatchedRequestArgumentBinder = adapted instanceof UnmatchedRequestArgumentBinder;
            if (!isUnmatchedRequestArgumentBinder) {
                return Optional.of((context, source1) -> adapted.bind(context, source1.getOriginatingRequest()));
            }
        }

        ArgumentBinder<T, WebSocketState> binder = (ArgumentBinder<T, WebSocketState>) byType.get(argument.getType());
        if (binder != null) {
            return Optional.of(binder);
        }
        return Optional.of((context, source) -> {
            ConvertibleValues<Object> uriVariables = source.getSession().getUriVariables();
            if (uriVariables.contains(argument.getName())) {
                Optional<T> val = uriVariables.get(argument.getName(), argument);
                return val.isEmpty() ? ArgumentBinder.BindingResult.UNSATISFIED : () -> val;
            }
            return (ArgumentBinder.BindingResult<T>) queryValueArgumentBinder.bind((ArgumentConversionContext<Object>) context, source.getOriginatingRequest());
        });

    }
}
