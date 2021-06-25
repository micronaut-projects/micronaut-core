/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.reactor.http.server.netty.binders;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.server.netty.HttpContentProcessorResolver;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * Adds  Project Reactor request argument binders.
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Singleton
@Internal
@Requires(classes = Mono.class)
class ReactorNettyBinderRegistrar implements BeanCreatedEventListener<RequestBinderRegistry> {
    private final ConversionService<?> conversionService;
    private final HttpContentProcessorResolver httpContentProcessorResolver;

    /**
     * Default constructor.
     *
     * @param conversionService            The conversion service
     * @param httpContentProcessorResolver The processor resolver
     */
    ReactorNettyBinderRegistrar(
            @Nullable ConversionService<?> conversionService,
            HttpContentProcessorResolver httpContentProcessorResolver) {
        this.conversionService = conversionService == null ? ConversionService.SHARED : conversionService;
        this.httpContentProcessorResolver = httpContentProcessorResolver;
    }

    @Override
    public RequestBinderRegistry onCreated(BeanCreatedEvent<RequestBinderRegistry> event) {
        RequestBinderRegistry registry = event.getBean();
        registry.addRequestArgumentBinder(new MonoBodyBinder(
                conversionService,
                httpContentProcessorResolver
        ));
        return registry;
    }
}
