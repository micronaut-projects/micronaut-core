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
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.web.router.builder.FormData;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Binds the {@link FormData} of a form handler route: reads every field of the form, keeping
 * text fields as strings and storing file parts as {@link CompletedFileUpload}s.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class FormDataArgumentBinder implements TypedRequestArgumentBinder<FormData> {
    private static final Argument<FormData> ARGUMENT = Argument.of(FormData.class);

    private final BeanProvider<FormFactory> formFactory;
    private final ConversionService conversionService;

    FormDataArgumentBinder(BeanProvider<FormFactory> formFactory, ConversionService conversionService) {
        this.formFactory = formFactory;
        this.conversionService = conversionService;
    }

    @Override
    public Argument<FormData> argumentType() {
        return ARGUMENT;
    }

    @Override
    public BindingResult<FormData> bind(ArgumentConversionContext<FormData> context, HttpRequest<?> source) {
        if (!(source instanceof FormCapableHttpRequest<?> request) || !request.hasFormBody()) {
            return BindingResult.unsatisfied();
        }
        FormFactory factory = formFactory.get();
        Charset charset = request.getCharacterEncoding();
        Map<String, List<String>> fields = new LinkedHashMap<>();
        Map<String, List<CompletedFileUpload>> files = new LinkedHashMap<>();
        // the parts of a form arrive in order: each one is read or stored before the next
        CompletableFuture<FormData> future = Flux.from(request.getRawFormFields())
            .concatMap(field -> Flux.from(ReactiveExecutionFlow.toPublisher(complete(factory, request, field, charset, fields, files))))
            .then(Mono.fromSupplier(() -> FormData.of(fields, files, conversionService)))
            .toFuture();

        BasicHttpAttributes.addRouteWaitsFor(source, CompletableFutureExecutionFlow.just(future));

        return new PendingRequestBindingResult<>() {

            @Override
            public boolean isPending() {
                return !future.isDone();
            }

            @Override
            public Optional<FormData> getValue() {
                return Optional.ofNullable(future.getNow(null));
            }
        };
    }

    private static ExecutionFlow<Boolean> complete(FormFactory factory,
                                                   FormCapableHttpRequest<?> request,
                                                   RawFormField field,
                                                   Charset charset,
                                                   Map<String, List<String>> fields,
                                                   Map<String, List<CompletedFileUpload>> files) {
        String name = field.metadata().name();
        if (name == null) {
            field.close();
            return ExecutionFlow.just(Boolean.TRUE);
        }
        if (field.metadata().fileName() != null) {
            return factory.completeFileUpload(request, field).map(upload -> {
                files.computeIfAbsent(name, k -> new ArrayList<>(1)).add(upload);
                return Boolean.TRUE;
            });
        }
        return InternalByteBody.bufferFlow(field.byteBody()).map(body -> {
            try (CloseableAvailableByteBody available = body) {
                fields.computeIfAbsent(name, k -> new ArrayList<>(1)).add(available.toString(charset));
            }
            return Boolean.TRUE;
        });
    }
}
