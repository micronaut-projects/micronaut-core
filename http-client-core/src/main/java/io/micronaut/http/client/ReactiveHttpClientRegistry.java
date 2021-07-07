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
package io.micronaut.http.client;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.websocket.WebSocketClient;

/**
 * Internal interface for managing the construction and lifecycle of instances of reactive clients.
 * Classes which extends from {@link HttpClient}.
 *
 * @author Sergio del Amo
 * @author graemerocher
 * @since 2.0
 * @param <T> Reactive HTTP Client
 * @param <E> Reactive Server Sent Events HTTP Client
 * @param <S> Reactive Streaming HTTP Client
 * @param <W> Web Socket Client
 */
@Internal
public interface ReactiveHttpClientRegistry<T extends HttpClient, E extends SseClient, S extends StreamingHttpClient, W extends WebSocketClient, P extends ProxyHttpClient> {

    /**
     * Return the client for the client ID and path.
     *
     * @param httpVersion The HTTP version
     * @param clientId    The client ID
     * @param path        The path (Optional)
     * @return The client
     */
    @NonNull
    T getClient(HttpVersion httpVersion, @NonNull String clientId, @Nullable String path);

    /**
     * Resolves a {@link HttpClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The HTTP Client
     */
    T resolveClient(@Nullable InjectionPoint injectionPoint,
                    @Nullable LoadBalancer loadBalancer,
                    @Nullable HttpClientConfiguration configuration,
                    BeanContext beanContext);

    /**
     * Resolves a {@link ProxyHttpClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The Proxy HTTP Client
     */
    P resolveProxyClient(@Nullable InjectionPoint injectionPoint,
                         @Nullable LoadBalancer loadBalancer,
                         @Nullable HttpClientConfiguration configuration,
                         BeanContext beanContext);

    /**
     * Resolves a {@link SseClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The SSE HTTP Client
     */
    E resolveSseClient(@Nullable InjectionPoint injectionPoint,
                    @Nullable LoadBalancer loadBalancer,
                    @Nullable HttpClientConfiguration configuration,
                    BeanContext beanContext);

    /**
     * Resolves a {@link StreamingHttpClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The Streaming HTTP Client
     */
    S resolveStreamingClient(@Nullable InjectionPoint injectionPoint,
                             @Nullable LoadBalancer loadBalancer,
                             @Nullable HttpClientConfiguration configuration,
                             BeanContext beanContext);

    /**
     * Resolves a {@link WebSocketClient} for the given injection point.
     *
     * @param injectionPoint The injection point
     * @param loadBalancer   The load balancer to use (Optional)
     * @param configuration  The configuration (Optional)
     * @param beanContext    The bean context to use
     * @return The Streaming HTTP Client
     */
    W resolveWebSocketClient(@Nullable InjectionPoint injectionPoint,
                             @Nullable LoadBalancer loadBalancer,
                             @Nullable HttpClientConfiguration configuration,
                             BeanContext beanContext);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    T getClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    E getSseClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    S getStreamingClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    W getWebSocketClient(@NonNull AnnotationMetadata annotationMetadata);

    /**
     * Dispose of the client defined by the given metadata.
     *
     * @param annotationMetadata The annotation metadata
     */
    void disposeClient(AnnotationMetadata annotationMetadata);

    /**
     * @return Return the default HTTP client.
     */
    default T getDefaultClient() {
        return getClient(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * @return Return the default Sse HTTP client.
     */
    default E getDefaultSseClient() {
        return getSseClient(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * @return Return the default Streaming HTTP client.
     */
    default S getDefaultStreamingClient() {
        return getStreamingClient(AnnotationMetadata.EMPTY_METADATA);
    }

    /**
     * @return Return the default Websocket HTTP client.
     */
    default W getDefaultWebSocketClient() {
        return getWebSocketClient(AnnotationMetadata.EMPTY_METADATA);
    }
}
