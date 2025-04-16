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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.reactivestreams.Publisher;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

/**
 * A {@link LoadBalancer} that resolves a fixed URI.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class FixedLoadBalancer implements LoadBalancer  {
    private final Publisher<ServiceInstance> publisher;
    private final ServiceInstance serviceInstance;
    private final URI uri;

    /**
     * Constructs a new FixedLoadBalancer.
     *
     * @param url The URL to fix to
     * @deprecated Use {@link #FixedLoadBalancer(URI)} instead
     */
    @Deprecated
    public FixedLoadBalancer(URL url) {
        this(toUriUnchecked(url));
    }

    /**
     * Constructs a new FixedLoadBalancer.
     *
     * @param uri The URI to fix to
     */
    public FixedLoadBalancer(URI uri) {
        this.uri = uri;
        serviceInstance = ServiceInstance.of(uri.getHost(), uri);
        this.publisher = Publishers.just(serviceInstance);
    }

    @Override
    public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
        return publisher;
    }

    /**
     * @return The URL of the {@link LoadBalancer}
     * @deprecated Use {@link #getUri()} instead
     */
    @Deprecated
    public URL getUrl() {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return The URI of the {@link LoadBalancer}
     */
    public URI getUri() {
        return uri;
    }

    /**
     * The fixed {@link ServiceInstance} returned by {@link #select}. Internal use only.
     *
     * @return The service instance
     */
    @Internal
    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }

    @Override
    public Optional<String> getContextPath() {
        return Optional.ofNullable(getUri().getPath());
    }

    private static URI toUriUnchecked(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Illegal URI", e);
        }
    }
}
