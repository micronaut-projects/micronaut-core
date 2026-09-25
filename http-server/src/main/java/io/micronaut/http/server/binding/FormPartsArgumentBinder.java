/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds the {@link FormParts} argument of a controller or filter method. Nothing is read until
 * the method consumes the parts, which are closed when the request ends.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class FormPartsArgumentBinder implements TypedRequestArgumentBinder<FormParts> {
    private static final Argument<FormParts> ARGUMENT = Argument.of(FormParts.class);

    private final BeanProvider<FormFactory> formFactory;
    private final ConversionService conversionService;

    FormPartsArgumentBinder(BeanProvider<FormFactory> formFactory, ConversionService conversionService) {
        this.formFactory = formFactory;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<FormParts> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<FormParts> bind(ArgumentConversionContext<FormParts> context, HttpRequest<?> source) {
        CompletableFuture<@Nullable FormData> replaced = FormBinding.isForm(source) ? FormBinding.replacedForm(source, conversionService) : null;
        if (replaced != null) {
            // a filter set the body: a cleared body has no parts, and the parts of an object
            // cannot be streamed
            return source.getBody().isPresent() ? BindingResult.unsatisfied() : () -> Optional.of(DefaultAsyncRequestBody.NoFormParts.INSTANCE);
        }
        FormCapableHttpRequest<?> request = FormBinding.formRequest(source);
        if (request == null) {
            return BindingResult.unsatisfied();
        }
        // one for the request, closed when the request ends
        FormParts parts = FormBinding.of(request).parts(formFactory.get());
        return () -> Optional.of(parts);
    }
}
