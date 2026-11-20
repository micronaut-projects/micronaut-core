/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.websocket.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.websocket.WebSocketPongMessage;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * The handler methods of a WebSocket bean, classified by their annotations: the methods the
 * {@link WebSocketBeanRegistry} calls, and the route of a {@code @ServerWebSocket} is built from.
 * An {@link OnMessage} method that takes a {@link WebSocketPongMessage} handles the pongs, and
 * not the messages. Of several methods of a kind, the last one is the handler.
 *
 * @param <T> The bean type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class WebSocketMethods<T> {

    private @Nullable ExecutableMethod<T, ?> onOpen;
    private @Nullable ExecutableMethod<T, ?> onClose;
    private @Nullable ExecutableMethod<T, ?> onMessage;
    private @Nullable ExecutableMethod<T, ?> onPong;
    private @Nullable ExecutableMethod<T, ?> onError;

    private WebSocketMethods() {
    }

    /**
     * Classify the methods of a WebSocket bean.
     *
     * @param methods The executable methods of the bean
     * @param <T>     The bean type
     * @return The handler methods
     */
    public static <T> WebSocketMethods<T> of(Collection<ExecutableMethod<T, ?>> methods) {
        WebSocketMethods<T> handlers = new WebSocketMethods<>();
        for (ExecutableMethod<T, ?> method : methods) {
            if (method.isAnnotationPresent(OnOpen.class)) {
                handlers.onOpen = method;
            } else if (method.isAnnotationPresent(OnClose.class)) {
                handlers.onClose = method;
            } else if (method.isAnnotationPresent(OnError.class)) {
                handlers.onError = method;
            } else if (method.isAnnotationPresent(OnMessage.class)) {
                if (Arrays.asList(method.getArgumentTypes()).contains(WebSocketPongMessage.class)) {
                    handlers.onPong = method;
                } else {
                    handlers.onMessage = method;
                }
            }
        }
        return handlers;
    }

    /**
     * @return The {@link OnOpen} method, or {@code null}
     */
    public @Nullable ExecutableMethod<T, ?> onOpen() {
        return onOpen;
    }

    /**
     * @return The {@link OnClose} method, or {@code null}
     */
    public @Nullable ExecutableMethod<T, ?> onClose() {
        return onClose;
    }

    /**
     * @return The {@link OnMessage} method of the messages, or {@code null}
     */
    public @Nullable ExecutableMethod<T, ?> onMessage() {
        return onMessage;
    }

    /**
     * @return The {@link OnMessage} method of the pongs, or {@code null}
     */
    public @Nullable ExecutableMethod<T, ?> onPong() {
        return onPong;
    }

    /**
     * @return The {@link OnError} method, or {@code null}
     */
    public @Nullable ExecutableMethod<T, ?> onError() {
        return onError;
    }
}
