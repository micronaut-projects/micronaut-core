/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bind values annotated with {@link Part}.
 *
 * @param <T> The part type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class PartUploadAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<Part, T>, RequestArgumentBinder<T> {

    private final ConversionService conversionService;
    private final CompletedFileUploadBinder completedFileUploadBinder;
    private final PublisherPartUploadBinder publisherPartUploadBinder;
    private final BeanProvider<FormFactory> formFactory;

    /**
     * @param conversionService Conversion service
     * @param completedFileUploadBinder Completed upload binder
     * @param publisherPartUploadBinder Publisher part binder
     * @param formFactory Form utilities
     */
    public PartUploadAnnotationBinder(ConversionService conversionService,
                                    CompletedFileUploadBinder completedFileUploadBinder,
                                    PublisherPartUploadBinder publisherPartUploadBinder,
                                    BeanProvider<FormFactory> formFactory) {
        this.conversionService = conversionService;
        this.completedFileUploadBinder = completedFileUploadBinder;
        this.publisherPartUploadBinder = publisherPartUploadBinder;
        this.formFactory = formFactory;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        Argument<T> bound = context.getArgument();
        BindingResult<T> replaced = FormBinding.bindReplaced(context, request, conversionService, bound.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(bound.getName()));
        if (replaced != null) {
            // a filter set the body: the form it set, never the bytes of the request
            return replaced;
        }
        FormCapableHttpRequest<?> formRequest = FormBinding.formRequest(request);
        if (formRequest == null) {
            return BindingResult.unsatisfied();
        }
        if (FormBinding.isBound(context.getArgument())) {
            // FileUpload, List<FileUpload>, FormPart and their Optional
            return FormBinding.bind(context, request, formRequest, formFactory.get(), conversionService);
        }
        if (completedFileUploadBinder.matches(context.getArgument().getType())) {
            return completedFileUploadBinder.bind((ArgumentConversionContext) context, request);
        }
        if (publisherPartUploadBinder.matches(context.getArgument().getType())) {
            return publisherPartUploadBinder.bind((ArgumentConversionContext) context, request);
        }

        Argument<T> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        return bindPart(conversionService, context, formFactory.get(), request, formRequest, inputName, false);
    }

    /**
     * Bind one named field, sharing claims with the other form binders.
     *
     * @param conversionService Conversion service
     * @param context Conversion context
     * @param formFactory Form utilities
     * @param source The request being bound
     * @param formRequest The request supplying form fields
     * @param inputName The field name
     * @param skipClaimed Whether to skip an already claimed field
     * @param <T> The bound type
     * @return The binding result
     */
    public static <T> BindingResult<T> bindPart(ConversionService conversionService, ArgumentConversionContext<T> context, FormFactory formFactory, HttpRequest<?> source, FormCapableHttpRequest<?> formRequest, String inputName, boolean skipClaimed) {
        BindingResult<T> fromForm = FormBinding.bindField(conversionService, context, source, formRequest, formFactory, inputName);
        if (fromForm != null) {
            // the form is read whole by a FormData or FormParts argument of the route
            return fromForm;
        }
        FormRouteCompleter completer = formFactory.getOrCreateCompleter(formRequest);
        if (skipClaimed && completer.isClaimed(inputName)) {
            return BindingResult.unsatisfied();
        }
        CompletableFuture<@Nullable Optional<T>> completableFuture = FormFieldFlows.firstFlatMap(completer.subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_FULL, context.getArgument())),
            rff -> formFactory.completePart(formRequest, rff),
            d -> {
                boolean skipClose = false;
                try {
                    Optional<T> converted = conversionService.convert(d, context);
                    if (converted.isPresent() && converted.get() == d) {
                        skipClose = true;
                    }
                    return converted;
                } finally {
                    if (!skipClose) {
                        d.closeAsync(formFactory.getDiskWriteExecutor());
                    }
                }
            });
        BasicHttpAttributes.addRouteWaitsFor(formRequest, CompletableFutureExecutionFlow.just(completableFuture));

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !completableFuture.isDone();
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }

            @Override
            @SuppressWarnings("java:S2789") // first() returns null when the named form field is absent.
            public Optional<T> getValue() {
                Optional<T> res = completableFuture.getNow(Optional.empty());
                //noinspection OptionalAssignedToNull
                if (res == null) {
                    // tricky: If the field is missing, the future will return null here.
                    res = Optional.empty();
                }
                return res;
            }
        };
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
