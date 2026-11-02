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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.AsyncRequestBodySource;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Creates body readers for argument binding and {@link AsyncRequestBody#of(HttpRequest)}.
 * Creating a reader neither reads bytes nor changes the route's binding state.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
public final class AsyncRequestBodyFactory implements AsyncRequestBodySource {
    final ConversionService conversionService;
    // Providers break dependency cycles; resolve each service once, when a read needs it.
    private final Supplier<RequestBinderRegistry> binderRegistry;
    private final Supplier<MessageBodyHandlerRegistry> bodyHandlerRegistry;
    private final Supplier<FormFactory> formFactory;

    /**
     * @param argumentSatisfier The argument binding services
     * @param bodyHandlerRegistry The message body readers
     * @param formFactory The form factory
     * @param conversionService The conversion service
     */
    public AsyncRequestBodyFactory(BeanProvider<RequestArgumentSatisfier> argumentSatisfier,
                                  BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry,
                                  BeanProvider<FormFactory> formFactory,
                                  ConversionService conversionService) {
        this.binderRegistry = SupplierUtil.memoized(() -> argumentSatisfier.get().getBinderRegistry());
        this.bodyHandlerRegistry = SupplierUtil.memoized(bodyHandlerRegistry::get);
        this.formFactory = SupplierUtil.memoized(formFactory::get);
        this.conversionService = conversionService;
    }

    @Override
    public @Nullable AsyncRequestBody asyncRequestBody(HttpRequest<?> request) {
        return create(request);
    }

    /**
     * Create an independent reader of the current request view. Readers of the same byte body
     * share its lock and consumption state; each reader owns its reads and their cleanup.
     *
     * @param request The request view
     * @return The reader, or {@code null} if no server body is available
     */
    @Nullable DefaultAsyncRequestBody create(HttpRequest<?> request) {
        ServerHttpRequest<?> server = ServerRequestBody.isBodySet(request)
            ? ServerRequestBody.serverRequest(request)
            : ServerRequestBody.of(request);
        return server == null ? null : new DefaultAsyncRequestBody(request, server, this);
    }

    RequestBinderRegistry binderRegistry() {
        return binderRegistry.get();
    }

    MessageBodyHandlerRegistry bodyHandlerRegistry() {
        return bodyHandlerRegistry.get();
    }

    FormFactory formFactory() {
        return formFactory.get();
    }
}
