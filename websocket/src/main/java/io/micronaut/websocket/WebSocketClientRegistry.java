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

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.inject.InjectionPoint;

/**
 * Interface for managing the construction and lifecycle of instances of reactive {@link WebSocketClient} clients.
 *
 * @author Sergio del Amo
 * @since 3.0.0
 * @param <W> Web Socket Client
 */
public interface WebSocketClientRegistry<W extends WebSocketClient> {

    /**
     * Resolves a {@link WebSocketClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The Streaming HTTP Client
     */
    @NonNull
    W resolveWebSocketClient(@Nullable InjectionPoint<?> injectionPoint,
                             @Nullable LoadBalancer loadBalancer,
                             @Nullable HttpClientConfiguration configuration,
                             @NonNull BeanContext beanContext);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    W getWebSocketClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * @return Return the default Websocket HTTP client.
     */
    default W getDefaultWebSocketClient() {
        return getWebSocketClient(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * Dispose of the client defined by the given metadata.
     *
     * @param annotationMetadata The annotation metadata
     */
    void disposeClient(AnnotationMetadata annotationMetadata);
}
