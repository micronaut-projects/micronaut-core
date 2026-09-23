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
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Binds the {@link FormParts} argument of a controller method. Nothing is read until the method
 * consumes the parts, which are closed when the request ends. A handler route reads the parts
 * with {@link io.micronaut.http.AsyncServerHttpRequest#parts()} instead.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class FormPartsArgumentBinder implements TypedRequestArgumentBinder<FormParts> {
    private static final Argument<FormParts> ARGUMENT = Argument.of(FormParts.class);

    private final BeanProvider<FormFactory> formFactory;

    FormPartsArgumentBinder(BeanProvider<FormFactory> formFactory) {
        this.formFactory = formFactory;
    }

    @Override
    public Argument<FormParts> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<FormParts> bind(ArgumentConversionContext<FormParts> context, HttpRequest<?> source) {
        if (!(source instanceof FormCapableHttpRequest<?> request) || !request.hasFormBody()) {
            return BindingResult.unsatisfied();
        }
        FormFactory factory = formFactory.get();
        DefaultFormParts parts = new DefaultFormParts(request, UploadContext.of(factory, request));
        // closed when the request ends: the method may return before it read them, and failures
        // before it is called and disconnects end the request too
        request.addDisposalResource(parts::close);
        return () -> Optional.of(parts);
    }
}
