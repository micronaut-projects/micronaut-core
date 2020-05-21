/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.websocket.bind;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.ArgumentBinderRegistry;
import io.micronaut.core.bind.annotation.AnnotatedArgumentBinder;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.websocket.RxWebSocketSession;
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

    private final Map<Class, ArgumentBinder<?, WebSocketState>> byType = new HashMap<>(5);

    /**
     * Default constructor.
     *
     * @param requestBinderRegistry The request binder registry
     */
    public WebSocketStateBinderRegistry(ArgumentBinderRegistry<HttpRequest<?>> requestBinderRegistry) {
        this.requestBinderRegistry = requestBinderRegistry;
        ArgumentBinder<Object, WebSocketState> sessionBinder = (context, source) -> () -> Optional.of(source.getSession());
        this.byType.put(WebSocketSession.class, sessionBinder);
        this.byType.put(RxWebSocketSession.class, sessionBinder);
    }

    @Override
    public <T, ST> void addRequestArgumentBinder(ArgumentBinder<T, ST> binder) {
        requestBinderRegistry.addRequestArgumentBinder(binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, WebSocketState>> findArgumentBinder(Argument<T> argument, WebSocketState source) {
        Optional<ArgumentBinder<T, HttpRequest<?>>> argumentBinder = requestBinderRegistry.findArgumentBinder(argument, source.getOriginatingRequest());
        if (argumentBinder.isPresent()) {
            ArgumentBinder<T, HttpRequest<?>> adapted = argumentBinder.get();

            boolean isParameterBinder = adapted instanceof AnnotatedArgumentBinder && ((AnnotatedArgumentBinder) adapted).getAnnotationType() == QueryValue.class;
            if (!isParameterBinder) {
                return Optional.of((context, source1) -> adapted.bind(context, source.getOriginatingRequest()));
            }
        }

        ArgumentBinder binder = byType.get(argument.getType());
        if (binder != null) {
            //noinspection unchecked
            return Optional.of(binder);
        } else {
            ConvertibleValues<Object> uriVariables = source.getSession().getUriVariables();
            if (uriVariables.contains(argument.getName())) {
                return Optional.of((context, s) -> () -> uriVariables.get(argument.getName(), argument));
            }
        }
        return Optional.empty();
    }
}
