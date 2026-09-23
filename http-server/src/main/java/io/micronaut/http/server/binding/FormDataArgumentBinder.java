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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormData;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final Logger LOG = LoggerFactory.getLogger(FormDataArgumentBinder.class);

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
        // one form for the request, shared with the arguments taken from it
        CompletableFuture<FormData> future = FormBinding.of(request).form(formFactory.get(), conversionService);

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

    /**
     * Read every field of the form of a request, keeping text fields as strings and storing file
     * parts as {@link CompletedFileUpload}s. The request owns the files: it releases the files
     * that were not consumed when it ends, also when collecting the form fails part way.
     *
     * @param factory           The form factory
     * @param conversionService The conversion service of the form
     * @param request           The request, with a form body
     * @return Completes with the form
     */
    static CompletableFuture<FormData> collect(FormFactory factory, ConversionService conversionService, FormCapableHttpRequest<?> request) {
        return start(UploadContext.of(factory, request), factory, conversionService, request).result();
    }

    /**
     * Start reading every field of the form of a request, like {@link #collect}, in a collection
     * that can be cancelled: the fields that were not read yet are discarded.
     *
     * @param uploadContext     The context of the content of the fields
     * @param factory           The form factory
     * @param conversionService The conversion service of the form
     * @param request           The request, with a form body
     * @return The collection
     */
    static Collection start(UploadContext uploadContext, FormFactory factory, ConversionService conversionService, FormCapableHttpRequest<?> request) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        Map<String, List<FileUpload>> files = new LinkedHashMap<>();
        OwnedUploads owned = new OwnedUploads();
        // the request releases the files that were not consumed, also when collecting the form
        // fails part way, or the handler is never called; it stops reading a form it no longer
        // needs, and releases the files stored after it ended
        request.addDisposalResource(owned::close);
        AtomicLong textBytes = new AtomicLong();
        CompletableFuture<FormData> result = new CompletableFuture<>();
        // the parts of a form arrive in order: each one is read or stored before the next
        Disposable subscription = Flux.from(request.getRawFormFields())
            .concatMap(field -> Flux.from(ReactiveExecutionFlow.toPublisher(complete(factory, uploadContext, request, field, fields, files, owned, textBytes))))
            .then(Mono.fromSupplier(() -> form(fields, files, conversionService)))
            .subscribe(result::complete, result::completeExceptionally);
        owned.reading(subscription);
        return new Collection(result, subscription);
    }

    private static FormData form(Map<String, List<String>> fields, Map<String, List<FileUpload>> files, ConversionService conversionService) {
        Map<String, List<FileUpload>> immutable = new LinkedHashMap<>();
        files.forEach((name, list) -> immutable.put(name, List.copyOf(list)));
        return new DefaultFormData(fields, immutable, conversionService);
    }

    private static void release(List<FileUpload> uploads) {
        DefaultFormData.closeAll(List.of(uploads)).whenComplete((ignored, error) -> {
            if (error != null) {
                LOG.warn("Failed to release the uploaded files of a form", error);
            }
        });
    }

    private static ExecutionFlow<Boolean> complete(FormFactory factory,
                                                   UploadContext context,
                                                   FormCapableHttpRequest<?> request,
                                                   RawFormField field,
                                                   Map<String, List<String>> fields,
                                                   Map<String, List<FileUpload>> files,
                                                   OwnedUploads owned,
                                                   AtomicLong textBytes) {
        String name = field.metadata().name();
        if (name == null) {
            field.close();
            return ExecutionFlow.just(Boolean.TRUE);
        }
        if (field.metadata().fileName() != null) {
            // stored with the limits of the multipart configuration
            return factory.completeFileUpload(request, field).map(upload -> {
                // the request disposes of the upload it completed: this takes over the content
                FileUpload file = new DefaultFileUpload(new StoredUploadContent(upload.moveResource(), context));
                if (!owned.add(file)) {
                    // stored after the request ended
                    release(List.of(file));
                }
                files.computeIfAbsent(name, k -> new ArrayList<>(1)).add(file);
                return Boolean.TRUE;
            });
        }
        return InternalByteBody.bufferFlow(field.byteBody()).map(body -> {
            try (CloseableAvailableByteBody available = body) {
                // the text of a form is buffered: it counts against the buffer limit
                long total = textBytes.addAndGet(available.length());
                if (total > context.maxBufferSize()) {
                    throw new ContentLengthExceededException("The text fields of the form exceed the maximum allowed content length [" + context.maxBufferSize() + "]");
                }
                fields.computeIfAbsent(name, k -> new ArrayList<>(1)).add(available.toString(context.charset()));
            }
            return Boolean.TRUE;
        });
    }

    /**
     * A collection of a form that is running, or completed.
     *
     * @param result       Completes with the form
     * @param subscription The subscription to the fields of the form
     */
    record Collection(CompletableFuture<FormData> result, Disposable subscription) {

        /**
         * Stop reading the form, if it was not completely read: the rest of the body is
         * discarded, and the result fails. The files stored so far stay owned by the request.
         */
        void cancel() {
            if (result.isDone()) {
                return;
            }
            subscription.dispose();
            result.completeExceptionally(new CancellationException("The form was not read completely before the handler completed"));
        }
    }

    /**
     * The files of a form the request owns until it ends, and the reading of the form.
     */
    private static final class OwnedUploads {
        // guarded by this
        private final List<FileUpload> uploads = new ArrayList<>();
        private boolean closed;
        private @Nullable Disposable reading;

        synchronized boolean add(FileUpload upload) {
            if (closed) {
                return false;
            }
            uploads.add(upload);
            return true;
        }

        void reading(Disposable subscription) {
            boolean dispose;
            synchronized (this) {
                dispose = closed;
                reading = subscription;
            }
            if (dispose) {
                subscription.dispose();
            }
        }

        void close() {
            List<FileUpload> owned;
            Disposable subscription;
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                owned = List.copyOf(uploads);
                subscription = reading;
            }
            if (subscription != null) {
                // a form the request no longer needs: nothing to do once it was read
                subscription.dispose();
            }
            if (!owned.isEmpty()) {
                release(owned);
            }
        }
    }
}
