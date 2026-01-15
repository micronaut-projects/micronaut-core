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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.http.server.netty.NettyHttpRequest;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Bind publisher annotated {@link io.micronaut.http.annotation.Part}.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class NettyPublisherPartUploadBinder implements TypedRequestArgumentBinder<Publisher<?>>, NettyRequestArgumentBinder<Publisher<?>> {

    private static final Argument<Publisher<?>> PUBLISHER_ARGUMENT = (Argument) Argument.of(Publisher.class);

    private final ConversionService conversionService;
    private final BeanProvider<FormFactory> formFactory;

    NettyPublisherPartUploadBinder(ConversionService conversionService, BeanProvider<FormFactory> formFactory) {
        this.conversionService = conversionService;
        this.formFactory = formFactory;
    }

    @Override
    public BindingResult<Publisher<?>> bindForNettyRequest(ArgumentConversionContext<Publisher<?>> context,
                                                           NettyHttpRequest<?> request) {
        if (!request.hasFormBody()) {
            return BindingResult.unsatisfied();
        }

        Argument<Publisher<?>> argument = context.getArgument();
        String inputName = argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());

        Argument<?> contentArgument = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        Class<?> contentTypeClass = contentArgument.getType();

        Flux<?> publisher;
        if (contentTypeClass == StreamingFileUpload.class) {
            // Publisher<StreamingFileUpload>
            publisher = Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC, argument)))
                .map(f -> formFactory.get().streamFileUpload(f))
                .doOnDiscard(StreamingFileUpload.class, StreamingFileUpload::close);
        } else if (contentTypeClass == Publisher.class) {
            // Publisher<Publisher<…>>
            Argument<?> nestedType = contentArgument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            publisher = Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC, argument)))
                .mapNotNull(f -> Flux.from(f.byteBody().toReadBufferPublisher()).map(rb -> {
                    if (nestedType.isAssignableFrom(ReadBuffer.class)) {
                        return rb;
                    } else {
                        try (rb) {
                            return conversionService.convertRequired(rb, nestedType);
                        }
                    }
                }));
        } else if (contentTypeClass == PartData.class) {
            publisher = Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC, argument)))
                .concatMap(raw -> Flux.from(raw.byteBody().toReadBufferPublisher())
                    .map(rb -> new PartData(raw.metadata(), rb.move())))
                .doOnDiscard(PartData.class, PartData::close);
        } else if (contentTypeClass == CompletedFileUpload.class) {
            // Publisher<CompletedFileUpload>
            // these objects consume little memory, and accept writing to disk anyway, so we
            // subscribe eagerly here to avoid backpressure.
            publisher = Flux.from(Publishers.bufferNow(Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC_NO_BACKPRESSURE, argument)))
                .flatMap(f -> {
                    if (f.metadata().fileName() == null) {
                        f.close();
                        return Flux.error(new IllegalStateException("Field was not a file upload (no filename parameter)"));
                    }
                    return ReactiveExecutionFlow.toPublisher(formFactory.get().completePart(request, f));
                })));
        } else if (contentTypeClass == CompletedAttribute.class) {
            // Publisher<CompletedAttribute>
            publisher = Flux.from(Publishers.bufferNow(Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC_NO_BACKPRESSURE, argument)))
                .flatMap(f -> ReactiveExecutionFlow.toPublisher(formFactory.get().completeAttribute(request, f)))));
        } else {
            // Publisher<CompletedPart> or anything else
            Flux<CompletedPart> cpublisher = Flux.from(Publishers.bufferNow(Flux.from(formFactory.get().getOrCreateCompleter(request).subscribeField(inputName, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.ASYNC_NO_BACKPRESSURE, argument)))
                .flatMap(f -> ReactiveExecutionFlow.toPublisher(formFactory.get().completePart(request, f)))));
            if (contentTypeClass == CompletedPart.class) {
                publisher = cpublisher;
            } else {
                publisher = cpublisher.publishOn(Schedulers.fromExecutor(formFactory.get().getDiskWriteExecutor())).map(part -> {
                    try (part) {
                        return conversionService.convertRequired(part, contentArgument);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }

        return () -> Optional.of(publisher);
    }

    @Override
    public Argument<Publisher<?>> argumentType() {
        return PUBLISHER_ARGUMENT;
    }
}
