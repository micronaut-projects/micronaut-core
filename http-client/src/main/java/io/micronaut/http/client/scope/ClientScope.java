/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.client.Client;
import io.micronaut.http.client.DefaultHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.LoadBalancerResolver;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.ParametrizedProvider;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        Client annotation = argument.getAnnotation(Client.class);
        if (annotation == null) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called for injection point that is not annotated with @Client");
        }
        if (!HttpClient.class.isAssignableFrom(argument.getType())) {
            throw new DependencyInjectionException(resolutionContext, argument, "@Client used on type that is not an HttpClient");
        }
        if (!(provider instanceof ParametrizedProvider)) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called with invalid bean provider");
        }
        String[] value = annotation.value();
        if (ArrayUtils.isEmpty(value) || StringUtils.isEmpty(value[0])) {
            String serviceId = annotation.id();
            if (StringUtils.isEmpty(serviceId)) {
                throw new DependencyInjectionException(resolutionContext, argument, "No value specified for @Client");
            } else {
                value = new String[]{serviceId};
            }
        }

        String[] finalValue = value;
        LoadBalancer loadBalancer = loadBalancerResolver.resolve(value)
            .orElseThrow(() ->
                new DependencyInjectionException(resolutionContext, argument, "Invalid service reference [" + ArrayUtils.toString((Object[]) finalValue) + "] specified to @Client")
            );

        //noinspection unchecked
        return (T) clients.computeIfAbsent(new ClientKey(identifier, value), clientKey -> {
            String contextPath = null;
            String annotationPath = annotation.path();
            String[] annotationValue = annotation.value();
            if (StringUtils.isNotEmpty(annotationPath)) {
                contextPath = annotationPath;
            } else if (ArrayUtils.isNotEmpty(annotationValue) && annotationValue[0].startsWith("/")) {
                contextPath = annotationValue[0];
            } else {
                if (loadBalancer instanceof FixedLoadBalancer) {
                    contextPath = ((FixedLoadBalancer) loadBalancer).getUrl().getPath();
                }
            }
            HttpClientConfiguration configuration = beanContext.getBean(annotation.configuration());
            HttpClient httpClient = (HttpClient) ((ParametrizedProvider<T>) provider).get(loadBalancer, configuration, contextPath);
            if (httpClient instanceof DefaultHttpClient) {
                ((DefaultHttpClient) httpClient).setClientIdentifiers(finalValue);
            }
            return httpClient;
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
    private static class ClientKey {
        final BeanIdentifier identifier;
        final String[] value;

        public ClientKey(BeanIdentifier identifier, String[] value) {
            this.identifier = identifier;
            this.value = value;
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
            return Objects.equals(identifier, clientKey.identifier) &&
                Arrays.equals(value, clientKey.value);
        }

        @Override
        public int hashCode() {
            int result = identifier.hashCode();
            result = 31 * result + Arrays.hashCode(value);
            return result;
        }
    }
}
