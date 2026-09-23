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
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.AsyncServerHttpRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Binds the {@link AsyncServerHttpRequest} of an asynchronous handler: a view of the request,
 * created for the route invocation, that owns the one read of the body.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class AsyncServerHttpRequestArgumentBinder implements TypedRequestArgumentBinder<AsyncServerHttpRequest<?>> {
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final Argument<AsyncServerHttpRequest<?>> ARGUMENT = (Argument) Argument.of(AsyncServerHttpRequest.class);

    final ConversionService conversionService;
    private final BeanProvider<RequestArgumentSatisfier> argumentSatisfier;
    private final BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry;
    private final BeanProvider<FormFactory> formFactory;

    AsyncServerHttpRequestArgumentBinder(BeanProvider<RequestArgumentSatisfier> argumentSatisfier,
                                         BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry,
                                         BeanProvider<FormFactory> formFactory,
                                         ConversionService conversionService) {
        this.argumentSatisfier = argumentSatisfier;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.formFactory = formFactory;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<AsyncServerHttpRequest<?>> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<AsyncServerHttpRequest<?>> bind(ArgumentConversionContext<AsyncServerHttpRequest<?>> context, HttpRequest<?> source) {
        // the request a filter continued with reads the body of the server request it wraps
        ServerHttpRequest<?> server = ServerRequestBody.of(source);
        if (server == null) {
            return BindingResult.unsatisfied();
        }
        AsyncServerHttpRequest<?> asyncRequest = new DefaultAsyncServerHttpRequest<>(source, server, this);
        return () -> Optional.of(asyncRequest);
    }

    /**
     * @return The binders of the arguments of the routes, which bind the {@code @Body} arguments
     */
    RequestBinderRegistry binderRegistry() {
        return argumentSatisfier.get().getBinderRegistry();
    }

    /**
     * @return The message body readers
     */
    MessageBodyHandlerRegistry bodyHandlerRegistry() {
        return bodyHandlerRegistry.get();
    }

    /**
     * @return The form factory
     */
    FormFactory formFactory() {
        return formFactory.get();
    }
}
