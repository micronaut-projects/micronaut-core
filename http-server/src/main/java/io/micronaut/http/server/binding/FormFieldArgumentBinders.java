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
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * The binders of the form fields a route argument declares by type, bound by the name of the
 * argument (or of its {@link io.micronaut.http.annotation.Part}), see {@link FormBinding}:
 * {@link FileUpload}, {@code List<FileUpload>}, {@link FormPart}, and {@code Optional} of a
 * {@link FileUpload} or a {@link FormPart}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Factory
final class FormFieldArgumentBinders {

    private final BeanProvider<FormFactory> formFactory;
    private final ConversionService conversionService;

    FormFieldArgumentBinders(BeanProvider<FormFactory> formFactory, ConversionService conversionService) {
        this.formFactory = formFactory;
        this.conversionService = conversionService;
    }

    @Singleton
    TypedRequestArgumentBinder<FileUpload> fileUploadBinder() {
        return new Binder<>(Argument.of(FileUpload.class));
    }

    @Singleton
    TypedRequestArgumentBinder<List<FileUpload>> fileUploadsBinder() {
        return new Binder<>(Argument.listOf(FileUpload.class));
    }

    @Singleton
    TypedRequestArgumentBinder<Optional<FileUpload>> optionalFileUploadBinder() {
        return new Binder<>(Argument.optionalOf(FileUpload.class));
    }

    @Singleton
    TypedRequestArgumentBinder<FormPart> formPartBinder() {
        return new Binder<>(Argument.of(FormPart.class));
    }

    @Singleton
    TypedRequestArgumentBinder<Optional<FormPart>> optionalFormPartBinder() {
        return new Binder<>(Argument.optionalOf(FormPart.class));
    }

    /**
     * Binds an argument of one of the types with {@link FormBinding#bind}.
     *
     * @param <T> The type
     */
    private final class Binder<T> implements TypedRequestArgumentBinder<T> {
        private final Argument<T> type;

        Binder(Argument<T> type) {
            this.type = type;
        }

        @Override
        public Argument<T> argumentType() {
            return type;
        }

        @Override
        public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> source) {
            BindingResult<T> replaced = FormBinding.bindReplaced(context, source, conversionService, FormBinding.name(context.getArgument()));
            if (replaced != null) {
                // a filter set the body
                return replaced;
            }
            FormCapableHttpRequest<?> request = FormBinding.formRequest(source);
            if (request == null) {
                return BindingResult.unsatisfied();
            }
            return FormBinding.bind(context, source, request, formFactory.get(), conversionService);
        }
    }
}
