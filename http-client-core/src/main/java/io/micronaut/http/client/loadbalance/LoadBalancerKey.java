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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * The key of a request for the load balancer, e.g. a session or user id: the
 * {@link LoadBalancerStrategy#STICKY sticky} strategy sends the requests of the same key to the
 * same instance. A request has a key when its {@link #ATTRIBUTE attribute} is set, or when the
 * service names a header to read it from with
 * {@code micronaut.http.services.<id>.load-balancer-key-header}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class LoadBalancerKey {

    /**
     * The request attribute that holds the key of the request for the load balancer. Its string
     * form is hashed, so it should be stable, e.g. a {@code String} or a {@code Long}.
     */
    public static final String ATTRIBUTE = "micronaut.http.client.load-balancer.key";

    private LoadBalancerKey() {
    }

    /**
     * Set the key of a request.
     *
     * @param request The request
     * @param key     The key
     * @param <R>     The request type
     * @return The request
     */
    public static <R extends MutableHttpRequest<?>> R set(R request, Object key) {
        request.setAttribute(ATTRIBUTE, key);
        return request;
    }

    /**
     * The discriminator a client selects the instance of a request with: the request, whose
     * {@link #ATTRIBUTE key attribute} is set from the key header of the service, if it names
     * one and the request has no key yet.
     *
     * @param request       The request
     * @param configuration The configuration of the client
     * @return The discriminator
     */
    @Internal
    public static Object discriminator(HttpRequest<?> request, HttpClientConfiguration configuration) {
        if (configuration instanceof ServiceHttpClientConfiguration service) {
            String header = service.getLoadBalancerKeyHeader();
            if (header != null && !header.isBlank() && request.getAttribute(ATTRIBUTE).isEmpty()) {
                String value = request.getHeaders().get(header);
                if (value != null) {
                    request.setAttribute(ATTRIBUTE, value);
                }
            }
        }
        return request;
    }

    /**
     * The key of a selection: the {@link #ATTRIBUTE key attribute} of a request, or any other
     * discriminator as it is.
     *
     * @param discriminator The discriminator of the selection, if any
     * @return The key, or {@code null} for none
     */
    public static @Nullable Object of(@Nullable Object discriminator) {
        if (discriminator instanceof HttpRequest<?> request) {
            return request.getAttribute(ATTRIBUTE).orElse(null);
        }
        return discriminator;
    }
}
