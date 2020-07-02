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
package io.micronaut.websocket.interceptor;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketClientException;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Intercepts unimplemented {@link io.micronaut.websocket.annotation.ClientWebSocket} methods.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
public class ClientWebSocketInterceptor implements MethodInterceptor<Object, Object> {

    private WebSocketSession webSocketSession;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Class<?> declaringType = context.getDeclaringType();
        if (declaringType == WebSocketSessionAware.class) {
            Object[] values = context.getParameterValues();
            if (ArrayUtils.isNotEmpty(values)) {
                Object o = values[0];
                if (o instanceof WebSocketSession) {
                    this.webSocketSession = (WebSocketSession) o;
                    return null;
                }
            }
        }
        if (declaringType == Closeable.class || declaringType == AutoCloseable.class) {
            // must be close method
            if (webSocketSession != null) {
                webSocketSession.close();
            }
            return null;
        } else {
            String methodName = context.getMethodName();
            if (methodName.startsWith("send") || methodName.startsWith("broadcast")) {
                MediaType mediaType = context.stringValue(Produces.class).map(MediaType::new).orElse(MediaType.APPLICATION_JSON_TYPE);
                validateSession();
                Class<?> javaReturnType = context.getReturnType().getType();
                if (void.class == javaReturnType) {
                    Object[] parameterValues = context.getParameterValues();
                    switch (parameterValues.length) {
                        case 0:
                            throw new IllegalArgumentException("At least 1 parameter is required to a send method");
                        case 1:
                            Object v = parameterValues[0];
                            if (v == null) {
                                throw new IllegalArgumentException("Parameter cannot be null");
                            }
                            webSocketSession.sendSync(v, mediaType);
                            return null;
                        default:
                            Map<String, Object> map = context.getParameterValueMap();
                            webSocketSession.sendSync(map, mediaType);
                            break;
                    }
                } else if (Future.class.isAssignableFrom(javaReturnType)) {
                    Object[] parameterValues = context.getParameterValues();
                    switch (parameterValues.length) {
                        case 0:
                            throw new IllegalArgumentException("At least 1 parameter is required to a send method");
                        case 1:
                            Object v = parameterValues[0];
                            if (v == null) {
                                throw new IllegalArgumentException("Parameter cannot be null");
                            }
                            return webSocketSession.sendAsync(v, mediaType);
                        default:
                            Map<String, Object> map = context.getParameterValueMap();
                            return webSocketSession.sendAsync(map, mediaType);
                    }
                } else if (Publishers.isConvertibleToPublisher(javaReturnType)) {
                    Object[] parameterValues = context.getParameterValues();
                    switch (parameterValues.length) {
                        case 0:
                            throw new IllegalArgumentException("At least 1 parameter is required to a send method");
                        case 1:
                            Object v = parameterValues[0];
                            if (v == null) {
                                throw new IllegalArgumentException("Parameter cannot be null");
                            }
                            return Publishers.convertPublisher(webSocketSession.send(v, mediaType), javaReturnType);
                        default:
                            Map<String, Object> map = context.getParameterValueMap();
                            return Publishers.convertPublisher(webSocketSession.send(map, mediaType), javaReturnType);
                    }
                }
            }
        }
        return context.proceed();
    }

    private void validateSession() {
        if (webSocketSession == null || !webSocketSession.isOpen()) {
            throw new WebSocketClientException("No available and open WebSocket session");
        }
    }
}
