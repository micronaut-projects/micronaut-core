/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.scope;

import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.LifeCycle;
import org.particleframework.context.exceptions.DependencyInjectionException;
import org.particleframework.context.scope.CustomScope;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.client.Client;
import org.particleframework.http.client.HttpClient;
import org.particleframework.http.client.ServerSelector;
import org.particleframework.http.client.ServerSelectorResolver;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanIdentifier;
import org.particleframework.inject.ParametrizedProvider;
import org.particleframework.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A scope for injecting {@link org.particleframework.http.client.HttpClient} implementations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class ClientScope implements CustomScope<Client>, LifeCycle<ClientScope> {

    private static final Logger LOG = LoggerFactory.getLogger(ClientScope.class);
    private final Map<ClientKey, HttpClient> clients = new ConcurrentHashMap<>();
    private final ServerSelectorResolver serverSelectorResolver;

    public ClientScope(ServerSelectorResolver serverSelectorResolver) {
        this.serverSelectorResolver = serverSelectorResolver;
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
        BeanResolutionContext.Segment segment = resolutionContext.getPath().currentSegment().orElseThrow(()->
            new IllegalStateException("@Client used in invalid location")
        );
        Argument argument = segment.getArgument();
        Client annotation = argument.getAnnotation(Client.class);
        if(annotation == null) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called for injection point that is not annotated with @Client");
        }
        if(!HttpClient.class.isAssignableFrom(beanDefinition.getBeanType())) {
            throw new DependencyInjectionException(resolutionContext, argument, "@Client used on type that is not an HttpClient");
        }
        if(!(provider instanceof ParametrizedProvider)) {
            throw new DependencyInjectionException(resolutionContext, argument, "ClientScope called with invalid bean provider");
        }
        String[] value = annotation.value();
        if(ArrayUtils.isEmpty(value) || StringUtils.isEmpty(value[0])) {
            throw new DependencyInjectionException(resolutionContext, argument, "No value specified for @Client");
        }

        ServerSelector serverSelector = serverSelectorResolver.resolve(value)
                                                                        .orElseThrow(()->
                                                                            new DependencyInjectionException(resolutionContext, argument, "Invalid service reference ["+ArrayUtils.toString((Object[]) value)+"] specified to @Client")
                                                                        );
        //noinspection unchecked
        return (T) clients.computeIfAbsent(new ClientKey(identifier, value), clientKey -> {
            HttpClient httpClient = (HttpClient) ((ParametrizedProvider<T>) provider).get(serverSelector);
            httpClient.setClientIdentifiers(value);
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
                if(LOG.isWarnEnabled()) {
                    LOG.warn("Error shutting down HTTP client: " + e.getMessage(), e);
                }
            }
        }
        clients.clear();
        return this;
    }

    private static class ClientKey {
        final BeanIdentifier identifier;
        final String[] value;

        public ClientKey(BeanIdentifier identifier, String[] value) {
            this.identifier = identifier;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
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
