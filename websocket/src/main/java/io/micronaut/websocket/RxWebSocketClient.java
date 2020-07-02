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
package io.micronaut.websocket;

import io.micronaut.http.MutableHttpRequest;
import io.reactivex.Flowable;

import java.net.URI;
import java.util.Map;

/**
 * Specialization of the {@link WebSocketClient} interface for RxJava.
 *
 * @author graemerocher
 * @since 1.0
 * @see WebSocketClient
 */
public interface RxWebSocketClient extends WebSocketClient {

    @Override
    <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request);

    /**
     * {@inheritDoc}
     */
    @Override
    default <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, URI uri) {
        return (Flowable<T>) WebSocketClient.super.connect(clientEndpointType, uri);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters);

    @Override
    default <T extends AutoCloseable> Flowable<T> connect(Class<T> clientEndpointType, String uri) {
        return (Flowable<T>) WebSocketClient.super.connect(clientEndpointType, uri);
    }
}
