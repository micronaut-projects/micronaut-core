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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpVersion;
import io.micronaut.inject.InjectionPoint;

/**
 * Interface for managing the construction and lifecycle of instances of {@link HttpClient} clients.
 *
 * @author Sergio del Amo
 * @since 3.0.0
 * @param <T> Reactive HTTP Client
 */
public interface HttpClientRegistry<T extends HttpClient> {

    /**
     * Return the client for the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata.
     * @return The client
     */
    @NonNull
    T getClient(@NonNull AnnotationMetadata annotationMetadata);

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
    @NonNull
    T resolveClient(@Nullable InjectionPoint<?> injectionPoint,
                    @Nullable LoadBalancer loadBalancer,
                    @Nullable HttpClientConfiguration configuration,
                    @NonNull BeanContext beanContext);

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
}
