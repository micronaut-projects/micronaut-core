/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.scope;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.client.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.filter.HttpClientFilterResolver;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.ParametrizedProvider;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.websocket.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A scope for injecting {@link io.micronaut.http.client.HttpClient} implementations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class ClientScope implements CustomScope<Client>, LifeCycle<ClientScope>, ApplicationEventListener<RefreshEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ClientScope.class);
    private final Map<ClientKey, HttpClient> clients = new ConcurrentHashMap<>();
    private final LoadBalancerResolver loadBalancerResolver;
    private final BeanContext beanContext;

    /**
     * @param loadBalancerResolver The load balancer resolver
     * @param beanContext          The bean context
     */
    public ClientScope(LoadBalancerResolver loadBalancerResolver, BeanContext beanContext) {
        this.loadBalancerResolver = loadBalancerResolver;
        this.beanContext = beanContext;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Class<Client> annotationType() {
        return Client.class;
    }

    @Override
    public <T> T get(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        BeanResolutionContext.Segment segment = resolutionContext.getPath().currentSegment().orElseThrow(() ->
            new IllegalStateException("@Client used in invalid location")
        );
        Argument argument = segment.getArgument();
        AnnotationValue<Client> annotation = argument.getAnnotationMetadata().findAnnotation(Client.class).orElse(null);
        if (annotation == null) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called for injection point that is not annotated with @Client");
        }
        if (!HttpClient.class.isAssignableFrom(argument.getType()) && !WebSocketClient.class.isAssignableFrom(argument.getType())) {
            throw new DependencyInjectionException(resolutionContext, argument, "@Client used on type that is not an HttpClient");
        }
        if (!(provider instanceof ParametrizedProvider)) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called with invalid bean provider");
        }
        String value = annotation.stringValue().orElseThrow(() ->
                new DependencyInjectionException(resolutionContext, argument, "No value specified for @Client")
        );

        AnnotationMetadata metadata = argument.getAnnotationMetadata();
        Optional<AnnotationValue> filterAnnotation = metadata
                .getAnnotationNameByStereotype(FilterMatcher.class)
                .map(metadata::getAnnotation);

        String path = annotation.get("path", String.class).orElse(null);
        Optional<Class<?>> configurationClass = annotation.classValue("configuration");

        //noinspection unchecked
        return (T) clients.computeIfAbsent(new ClientKey(value, filterAnnotation.map(AnnotationValue::getAnnotationName).orElse(null), path, configurationClass.orElse(null)), clientKey -> {
            HttpClient clientBean = beanContext.findBean(HttpClient.class, Qualifiers.byName(value)).orElse(null);

            if (clientBean != null && path == null && !configurationClass.isPresent() && !filterAnnotation.isPresent()) {
                return clientBean;
            }

            LoadBalancer loadBalancer = loadBalancerResolver.resolve(value)
                    .orElseThrow(() ->
                            new DependencyInjectionException(resolutionContext, argument, "Invalid service reference [" + value + "] specified to @Client")
                    );

            String contextPath = null;
            if (StringUtils.isNotEmpty(path)) {
                contextPath = path;
            } else if (StringUtils.isNotEmpty(value) && value.startsWith("/")) {
                contextPath = value;
            } else {
                if (loadBalancer instanceof FixedLoadBalancer) {
                    contextPath = ((FixedLoadBalancer) loadBalancer).getUrl().getPath();
                }
            }

            HttpClientConfiguration configuration;
            Optional<HttpClientConfiguration> clientSpecificConfig = beanContext.findBean(
                    HttpClientConfiguration.class,
                    Qualifiers.byName(value)
            );
            Class<HttpClientConfiguration> defaultConfiguration = (Class<HttpClientConfiguration>) configurationClass.orElse(HttpClientConfiguration.class);
            configuration = clientSpecificConfig.orElseGet(() -> beanContext.getBean(defaultConfiguration));

            if (!(configuration instanceof HttpClientConfiguration)) {
                throw new IllegalStateException("Referenced HTTP client configuration class must be an instance of HttpClientConfiguration for injection point: " + segment);
            }

            HttpClientFilterResolver filterResolver = beanContext.createBean(HttpClientFilterResolver.class,
                    Collections.singleton(value),
                    filterAnnotation.orElse(null));

            return (HttpClient) ((ParametrizedProvider<T>) provider).get(loadBalancer, configuration, contextPath, filterResolver);
        });
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.empty();
    }

    @Override
    public ClientScope stop() {
        for (HttpClient httpClient : clients.values()) {
            try {
                httpClient.close();
            } catch (Throwable e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down HTTP client: " + e.getMessage(), e);
                }
            }
        }
        clients.clear();
        return this;
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        refresh();
    }

    /**
     * Client key.
     */
    private static final class ClientKey {
        final String clientId;
        final String filterAnnotation;
        final String path;
        final Class<?> configurationClass;

        ClientKey(String clientId, String filterAnnotation, String path, Class<?> configurationClass) {
            this.clientId = clientId;
            this.filterAnnotation = filterAnnotation;
            this.path = path;
            this.configurationClass = configurationClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ClientKey clientKey = (ClientKey) o;
            return Objects.equals(clientId, clientKey.clientId) &&
                    Objects.equals(filterAnnotation, clientKey.filterAnnotation) &&
                    Objects.equals(path, clientKey.path) &&
                    Objects.equals(configurationClass, clientKey.configurationClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clientId, filterAnnotation, path, configurationClass);
        }
    }
}
