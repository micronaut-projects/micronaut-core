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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.server.multipart.FirstElementFlow;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.http.server.netty.NettyHttpRequest;

import java.util.List;
import java.util.Optional;

/**
 * Bind values annotated with {@link Part}.
 *
 * @param <T> The part type
 * @author Denis Stepanov
 * @since 4.0.0
 */
final class NettyPartUploadAnnotationBinder<T> implements AnnotatedRequestArgumentBinder<Part, T>, RequestArgumentBinder<T> {

    private final ConversionService conversionService;
    private final NettyCompletedFileUploadBinder completedFileUploadBinder;
    private final NettyPublisherPartUploadBinder publisherPartUploadBinder;
    private final BeanProvider<FormFactory> formFactory;

    NettyPartUploadAnnotationBinder(ConversionService conversionService,
                                    NettyCompletedFileUploadBinder completedFileUploadBinder,
                                    NettyPublisherPartUploadBinder publisherPartUploadBinder,
                                    BeanProvider<FormFactory> formFactory) {
        this.conversionService = conversionService;
        this.completedFileUploadBinder = completedFileUploadBinder;
        this.publisherPartUploadBinder = publisherPartUploadBinder;
        this.formFactory = formFactory;
    }

    @Override
    public BindingResult<T> bind(ArgumentConversionContext<T> context, HttpRequest<?> request) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        FormCapableHttpRequest<?> nettyRequest = request instanceof FormCapableHttpRequest<?> formRequest ? formRequest : NettyHttpRequest.findBodyRequest(request);
        if (nettyRequest == null || !nettyRequest.hasFormBody()) {
            return BindingResult.unsatisfied();
        }
        if (completedFileUploadBinder.matches(context.getArgument().getType())) {
            return completedFileUploadBinder.bind((ArgumentConversionContext) context, request);
        }
        if (publisherPartUploadBinder.matches(context.getArgument().getType())) {
            return publisherPartUploadBinder.bind((ArgumentConversionContext) context, request);
        }

        Argument<T> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        return bindPart(conversionService, context, formFactory.get(), nettyRequest, inputName, false);
    }

    static <T> BindingResult<T> bindPart(ConversionService conversionService, ArgumentConversionContext<T> context, FormFactory formFactory, FormCapableHttpRequest<?> nettyRequest, String inputName, boolean skipClaimed) {
        FormRouteCompleter completer = formFactory.getOrCreateCompleter(nettyRequest);
        if (skipClaimed && completer.isClaimed(inputName)) {
            return BindingResult.unsatisfied();
        }
        ExecutionFlow<Optional<T>> flow = FirstElementFlow.first(completer.subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_FULL, context.getArgument())))
            .flatMap(rff -> formFactory.completePart(nettyRequest, rff).map(d -> {
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
            }));
        FirstElementFlow.Settled<Optional<T>> settled = FirstElementFlow.settle(flow);
        BasicHttpAttributes.addRouteWaitsFor(nettyRequest, settled.flow());

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !settled.isDone();
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }

            @Override
            public Optional<T> getValue() {
                // an empty flow (no such part) yields no value
                return settled.valueNow().flatMap(r -> r);
            }
        };
    }

    @Override
    public Class<Part> getAnnotationType() {
        return Part.class;
    }
}
