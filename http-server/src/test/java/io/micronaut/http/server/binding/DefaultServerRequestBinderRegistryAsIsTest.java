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
package io.micronaut.http.server.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.server.multipart.FormFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subclass of the registry keeps the fast path of the request argument while it binds it with the default binder.
 */
class DefaultServerRequestBinderRegistryAsIsTest {

    private static final Argument<HttpRequest> REQUEST = Argument.of(HttpRequest.class);

    @Test
    void theDefaultRegistryBindsTheRequestAsIs() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertTrue(new DefaultServerRequestBinderRegistry(ConversionService.SHARED, List.of(), ctx.getBean(ServerBodyAnnotationBinder.class), ctx.getProvider(FormFactory.class))
                .bindsRequestAsIs(REQUEST));
        }
    }

    @Test
    void aSubclassThatKeepsTheDefaultBinderBindsTheRequestAsIs() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertTrue(new DefaultServerRequestBinderRegistry(ConversionService.SHARED, List.of(), ctx.getBean(ServerBodyAnnotationBinder.class), ctx.getProvider(FormFactory.class)) {
            }.bindsRequestAsIs(REQUEST));
        }
    }

    @Test
    void aSubclassThatOverridesTheLookupOfTheRequestDoesNotBindItAsIs() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            TypedRequestArgumentBinder<HttpRequest> custom = new TypedRequestArgumentBinder<>() {
                @Override
                public Argument<HttpRequest> argumentType() {
                    return REQUEST;
                }

                @Override
                public BindingResult<HttpRequest> bind(io.micronaut.core.convert.ArgumentConversionContext<HttpRequest> context, HttpRequest<?> source) {
                    return () -> Optional.of(source);
                }
            };
            assertFalse(new DefaultServerRequestBinderRegistry(ConversionService.SHARED, List.of(), ctx.getBean(ServerBodyAnnotationBinder.class), ctx.getProvider(FormFactory.class)) {
                @Override
                @SuppressWarnings("unchecked")
                public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
                    if (argument.getType() == HttpRequest.class) {
                        return Optional.of((ArgumentBinder<T, HttpRequest<?>>) (ArgumentBinder<?, HttpRequest<?>>) custom);
                    }
                    return super.findArgumentBinder(argument);
                }
            }.bindsRequestAsIs(REQUEST));
        }
    }

    @Test
    void aSubclassThatRegistersAnotherBinderOfTheRequestDoesNotBindItAsIs() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            DefaultServerRequestBinderRegistry registry = new DefaultServerRequestBinderRegistry(ConversionService.SHARED, List.of(), ctx.getBean(ServerBodyAnnotationBinder.class), ctx.getProvider(FormFactory.class)) {
            };
            registry.addArgumentBinder(new TypedRequestArgumentBinder<HttpRequest>() {
                @Override
                public Argument<HttpRequest> argumentType() {
                    return REQUEST;
                }

                @Override
                public BindingResult<HttpRequest> bind(io.micronaut.core.convert.ArgumentConversionContext<HttpRequest> context, HttpRequest<?> source) {
                    return () -> Optional.of(source);
                }
            });
            assertFalse(registry.bindsRequestAsIs(REQUEST));
        }
    }
}
