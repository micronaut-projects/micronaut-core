/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.DefaultRequestBinderRegistry;
import io.micronaut.http.bind.ServerRequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Shared server request binder registration, independent of the transport.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
@Singleton
@io.micronaut.core.annotation.Order(100) // Preserve precedence of custom and generic registries.
public class DefaultServerRequestBinderRegistry implements ServerRequestBinderRegistry {

    private final DefaultRequestBinderRegistry internalRequestBinderRegistry;

    /**
     * @param conversionService Conversion service
     * @param binders Custom argument binders
     * @param bodyBinder Shared body binder or its transport adapter
     * @param formFactory Form utilities
     */
    @Inject
    public DefaultServerRequestBinderRegistry(ConversionService conversionService,
                                            List<RequestArgumentBinder> binders,
                                            ServerBodyAnnotationBinder<Object> bodyBinder,
                                            BeanProvider<FormFactory> formFactory) {
        internalRequestBinderRegistry = new DefaultRequestBinderRegistry(conversionService, binders, bodyBinder);

        internalRequestBinderRegistry.addArgumentBinder(new CompletableFutureBodyBinder(bodyBinder));
        internalRequestBinderRegistry.addArgumentBinder(new PublisherBodyBinder(bodyBinder, conversionService));
        internalRequestBinderRegistry.addArgumentBinder(new MultipartBodyArgumentBinder(
            formFactory
        ));
        internalRequestBinderRegistry.addArgumentBinder(new InputStreamBodyBinder(bodyBinder));
        internalRequestBinderRegistry.addArgumentBinder(new StreamingFileUploadBinder(formFactory));
        CompletedFileUploadBinder completedFileUploadBinder = new CompletedFileUploadBinder(formFactory);
        internalRequestBinderRegistry.addArgumentBinder(completedFileUploadBinder);
        PublisherPartUploadBinder publisherPartUploadBinder = new PublisherPartUploadBinder(conversionService, formFactory);
        internalRequestBinderRegistry.addArgumentBinder(publisherPartUploadBinder);
        PartUploadAnnotationBinder<Object> partUploadAnnotationBinder = new PartUploadAnnotationBinder<>(
            conversionService,
            completedFileUploadBinder,
            publisherPartUploadBinder,
            formFactory
        );
        internalRequestBinderRegistry.addArgumentBinder(partUploadAnnotationBinder);

        internalRequestBinderRegistry.addUnmatchedRequestArgumentBinder(partUploadAnnotationBinder);
    }

    @Override
    public <T> void addArgumentBinder(ArgumentBinder<T, HttpRequest<?>> binder) {
        internalRequestBinderRegistry.addArgumentBinder(binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
        return internalRequestBinderRegistry.findArgumentBinder(argument);
    }

}
