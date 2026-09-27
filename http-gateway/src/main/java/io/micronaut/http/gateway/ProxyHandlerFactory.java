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
package io.micronaut.http.gateway;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.RawHttpClientRegistry;
import io.micronaut.http.server.util.ForwardedHeaders;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.retry.CircuitBreakerRegistry;
import io.micronaut.retry.RetryOperationsFactory;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the {@link ProxyHandler} of a {@link GatewayRoute}, e.g. to route to it with the
 * functional routes API:
 *
 * <pre>{@code
 * @Singleton
 * class Routes implements HttpRoutes {
 *     private final ProxyHandlerFactory proxies;
 *
 *     Routes(ProxyHandlerFactory proxies) {
 *         this.proxies = proxies;
 *     }
 *
 *     public void routes(HttpRouteBuilder routes) {
 *         routes.any("/api/orders/{+path}").consumesAll().handleAsync(proxies.create(GatewayRoute.builder("orders")
 *             .uri("lb://orders")
 *             .filters(GatewayFilters.stripPrefix(2))
 *             .build()));
 *     }
 * }
 * }</pre>
 *
 * The {@link GatewayRoutes gateway routes} do both at once.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
@Requires(beans = RawHttpClientRegistry.class)
public final class ProxyHandlerFactory {

    private final RawHttpClientRegistry clientRegistry;
    private final LoadBalancerResolver loadBalancerResolver;
    private final BeanLocator beanLocator;
    private final ForwardedHeaders forwardedHeaders;
    private final boolean xForwarded;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryOperationsFactory retryOperationsFactory;
    private final Map<String, LoadBalancer> loadBalancers = new ConcurrentHashMap<>();

    ProxyHandlerFactory(RawHttpClientRegistry clientRegistry,
                        LoadBalancerResolver loadBalancerResolver,
                        BeanLocator beanLocator,
                        GatewayConfiguration configuration,
                        CircuitBreakerRegistry circuitBreakerRegistry,
                        RetryOperationsFactory retryOperationsFactory) {
        this.clientRegistry = clientRegistry;
        this.loadBalancerResolver = loadBalancerResolver;
        this.beanLocator = beanLocator;
        this.forwardedHeaders = configuration.getForwarded().toForwardedHeaders();
        this.xForwarded = configuration.getForwarded().isXForwarded();
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryOperationsFactory = retryOperationsFactory;
    }

    /**
     * @param route The route
     * @return The handler that relays the requests of the route to its upstream
     */
    public ProxyHandler create(GatewayRoute route) {
        return new DefaultProxyHandler(Objects.requireNonNull(route, "route"), this);
    }

    /**
     * @param uri The URI of the upstream, see {@link GatewayRoute}
     * @return A handler that relays every request to the upstream, as a route whose id is the
     * URI
     */
    public ProxyHandler to(String uri) {
        return create(GatewayRoute.builder(uri).uri(uri).build());
    }

    ForwardedHeaders forwardedHeaders() {
        return forwardedHeaders;
    }

    boolean isXForwarded() {
        return xForwarded;
    }

    CircuitBreakerRegistry circuitBreakerRegistry() {
        return circuitBreakerRegistry;
    }

    RetryOperationsFactory retryOperationsFactory() {
        return retryOperationsFactory;
    }

    /**
     * @param route A load balanced route
     * @return The load balancer of its service: the one the client of the service reports the
     * outcomes of its exchanges to
     */
    LoadBalancer loadBalancer(GatewayRoute route) {
        String serviceId = route.getServiceId();
        return loadBalancers.computeIfAbsent(serviceId, id -> loadBalancerResolver.resolve(id)
            .orElseThrow(() -> new NoAvailableServiceException(id)));
    }

    /**
     * @param route A route
     * @return The HTTP version of the route, or of the client configuration of its service, or
     * of the default client configuration
     */
    HttpVersionSelection httpVersion(GatewayRoute route) {
        HttpVersionSelection version = route.getHttpVersion();
        if (version != null) {
            return version;
        }
        HttpClientConfiguration configuration = route.isLoadBalanced()
            ? beanLocator.findBean(HttpClientConfiguration.class, Qualifiers.byName(route.getServiceId()))
                .orElseGet(() -> beanLocator.getBean(DefaultHttpClientConfiguration.class))
            : beanLocator.getBean(DefaultHttpClientConfiguration.class);
        return HttpVersionSelection.forClientConfiguration(configuration);
    }

    /**
     * @param route   A route
     * @param version The HTTP version
     * @return The raw client of the upstream of the route: of its service, with the client
     * configuration of the service, or of its URI
     */
    AsyncRawHttpClient rawClient(GatewayRoute route, HttpVersionSelection version) {
        String clientId;
        if (route.isLoadBalanced()) {
            clientId = route.getServiceId();
        } else {
            URI uri = route.getUri();
            String path = uri.getRawPath();
            if (path != null && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            clientId = uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getRawAuthority() + (path == null ? "" : path);
        }
        return clientRegistry.getAsyncRawClient(version, clientId, null);
    }
}
