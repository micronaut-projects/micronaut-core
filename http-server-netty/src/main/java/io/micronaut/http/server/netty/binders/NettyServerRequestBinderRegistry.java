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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.type.Argument;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.ServerRequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.binding.DefaultServerRequestBinderRegistry;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;

import java.util.List;
import java.util.Optional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Compatibility facade for the shared server binder registry.
 *
 * @deprecated Use {@link DefaultServerRequestBinderRegistry}.
 * @since 4.0.0
 */
@Internal
@Singleton
@io.micronaut.context.annotation.Secondary
@Order(200)
@Deprecated(since = "5.3.0", forRemoval = true)
public final class NettyServerRequestBinderRegistry implements ServerRequestBinderRegistry {
    private final DefaultServerRequestBinderRegistry delegate;

    /** @param registry The registry used by the request lifecycle */
    @Inject
    public NettyServerRequestBinderRegistry(DefaultServerRequestBinderRegistry registry) {
        this.delegate = registry;
    }

    /**
     * @param conversionService Conversion service
     * @param binders Custom binders
     * @param httpServerConfiguration Legacy configuration provider
     * @param bodyHandlerRegistry Body readers
     * @param formFactory Form utilities
     */
    public NettyServerRequestBinderRegistry(ConversionService conversionService,
                                          List<RequestArgumentBinder> binders,
                                          BeanProvider<NettyHttpServerConfiguration> httpServerConfiguration,
                                          MessageBodyHandlerRegistry bodyHandlerRegistry,
                                          BeanProvider<FormFactory> formFactory) {
        this.delegate = new DefaultServerRequestBinderRegistry(conversionService, binders,
            new NettyBodyAnnotationBinder<>(conversionService, bodyHandlerRegistry, formFactory), formFactory);
    }

    @Override
    public <T> void addArgumentBinder(ArgumentBinder<T, HttpRequest<?>> binder) {
        delegate.addArgumentBinder(binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
        return delegate.findArgumentBinder(argument);
    }
}
