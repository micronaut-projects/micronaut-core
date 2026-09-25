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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Binds the {@link AsyncRequestBody} of an asynchronous handler: the body of the request,
 * created for the route invocation, that owns the one read of the body. What a read left open,
 * e.g. the parts of a form, is released when the stage returned by a handler route completes,
 * and for a controller method that declares the body, when the request ends.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class AsyncRequestBodyArgumentBinder implements TypedRequestArgumentBinder<AsyncRequestBody> {
    private static final Argument<AsyncRequestBody> ARGUMENT = Argument.of(AsyncRequestBody.class);

    final ConversionService conversionService;
    private final BeanProvider<RequestArgumentSatisfier> argumentSatisfier;
    private final BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry;
    private final BeanProvider<FormFactory> formFactory;

    AsyncRequestBodyArgumentBinder(BeanProvider<RequestArgumentSatisfier> argumentSatisfier,
                                   BeanProvider<MessageBodyHandlerRegistry> bodyHandlerRegistry,
                                   BeanProvider<FormFactory> formFactory,
                                   ConversionService conversionService) {
        this.argumentSatisfier = argumentSatisfier;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.formFactory = formFactory;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<AsyncRequestBody> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<AsyncRequestBody> bind(ArgumentConversionContext<AsyncRequestBody> context, HttpRequest<?> source) {
        // the request a filter continued with reads the body of the server request it wraps
        ServerHttpRequest<?> server = ServerRequestBody.isBodySet(source)
            // the body is the object a filter set, even none: the server request makes the empty body
            ? ServerRequestBody.serverRequest(source)
            : ServerRequestBody.of(source);
        if (server == null) {
            return BindingResult.unsatisfied();
        }
        AsyncRequestBody body = new DefaultAsyncRequestBody(source, server, this);
        return () -> Optional.of(body);
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
