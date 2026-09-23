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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormFieldException;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.CompletedAttribute;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.multipart.FormRouteCompleter;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * How the arguments of a route read the form of a request, decided once per request from the
 * arguments of the route, so that it does not depend on their order.
 *
 * <ul>
 *     <li><b>Fields</b>, when the route has no {@link FormData} and no {@link FormParts} argument:
 *     each argument reads the fields of its name through the {@link FormRouteCompleter}, like
 *     {@link CompletedFileUpload} arguments. A {@link FileUpload} (or a {@code List} of them) is
 *     received and stored before the route runs, a {@link FormPart} is handed over as its
 *     content starts to arrive, like a {@link StreamingFileUpload}. The fields no argument asks
 *     for are discarded.</li>
 *     <li><b>Collected</b>, when the route has a {@link FormData} argument: the whole form is
 *     read once, and the {@link FileUpload} arguments and the text arguments of the form (a
 *     {@link Part} or a parameter bound by name) are taken from it; a {@link FileUpload} argument
 *     is the handle {@link FormData#getFile(String)} returns.</li>
 *     <li><b>Streamed</b>, when the route has a {@link FormParts} argument: the parts read the
 *     whole form as it arrives. A parameter that is not annotated is not bound from the form.</li>
 * </ul>
 *
 * <p>Refused, before anything reads the form, with an {@link IllegalStateException} that names
 * the arguments (answered with 500): a {@link FormData} with a {@link FormParts}; a
 * {@link FormData} with an argument that reads its field by itself (a {@link FormPart} or a type
 * of {@code io.micronaut.http.multipart}); a {@link FormParts} with any argument that reads a
 * form field (a {@link Part}, a {@link FileUpload}, a {@link FormPart} or a type of
 * {@code io.micronaut.http.multipart}).</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class FormBinding {
    private static final String ATTRIBUTE = FormBinding.class.getName();
    private static final Logger LOG = LoggerFactory.getLogger(FormBinding.class);

    private final FormCapableHttpRequest<?> request;
    // guarded by this
    private Mode mode;
    private @Nullable String reader;
    private @Nullable String conflict;
    private @Nullable CompletableFuture<FormData> form;
    private @Nullable DefaultFormParts parts;

    private FormBinding(FormCapableHttpRequest<?> request) {
        this.request = request;
        Argument<?> data = null;
        Argument<?> streamed = null;
        // the first argument that reads its field as it arrives, and the first other argument
        // that reads a field by name
        Argument<?> streaming = null;
        Argument<?> named = null;
        RouteMatch<?> route = RouteAttributes.getRouteMatch(request).orElse(null);
        if (route instanceof MethodBasedRouteMatch<?, ?> method) {
            for (Argument<?> argument : method.getArguments()) {
                boolean part = argument.getAnnotationMetadata().hasAnnotation(Part.class);
                if (!part && argument.getAnnotationMetadata().hasStereotype(Bindable.class)) {
                    // read by the binder of its annotation, such as @Body
                    continue;
                }
                Class<?> type = argument.getType();
                Kind kind = Kind.of(argument);
                if (!part && type == FormData.class) {
                    data = data == null ? argument : data;
                } else if (!part && type == FormParts.class) {
                    streamed = streamed == null ? argument : streamed;
                } else if (kind == Kind.PART || readsItsField(type, part)) {
                    streaming = streaming == null ? argument : streaming;
                } else if (kind != null || part) {
                    named = named == null ? argument : named;
                }
            }
        }
        // refused before anything reads the form
        if (data != null && streamed != null) {
            mode = Mode.REFUSED;
            conflict = "The arguments [" + data + "] and [" + streamed + "] cannot be combined: a FormData reads the whole form before the route runs, "
                + "and FormParts stream it while the route runs. Declare one of them";
        } else if (data != null && streaming != null) {
            mode = Mode.REFUSED;
            conflict = "The argument [" + streaming + "] reads its form field by itself, so it cannot be combined with [" + data + "], which reads the whole form: "
                + "bind the file as a FileUpload, which is taken from the same form, or read it from the FormData";
        } else if (streamed != null && (streaming != null || named != null)) {
            mode = Mode.REFUSED;
            conflict = "The argument [" + (streaming != null ? streaming : named) + "] reads a form field, so it cannot be combined with [" + streamed + "], "
                + "which streams the whole form: read every field from the FormParts";
        } else if (data != null) {
            mode = Mode.COLLECTED;
            reader = data.toString();
        } else if (streamed != null) {
            mode = Mode.STREAMED;
            reader = streamed.toString();
        } else {
            mode = Mode.FIELDS;
        }
    }

    /**
     * @param type The type of an argument
     * @param part Whether the argument is annotated with {@link Part}
     * @return Whether the argument reads its form field by itself, through the
     * {@link FormRouteCompleter}, with a type of {@code io.micronaut.http.multipart}
     */
    private static boolean readsItsField(Class<?> type, boolean part) {
        return type == CompletedFileUpload.class
            || type == StreamingFileUpload.class
            || type == CompletedPart.class
            || type == CompletedAttribute.class
            || type == RawFormField.class
            || (part && Publisher.class.isAssignableFrom(type));
    }

    /**
     * The form binding of a request, created the first time an argument reads its form.
     *
     * @param request The request, with a form body
     * @return How the arguments of the route of the request read its form
     */
    static FormBinding of(FormCapableHttpRequest<?> request) {
        FormBinding existing = request.getAttribute(ATTRIBUTE, FormBinding.class).orElse(null);
        if (existing != null) {
            return existing;
        }
        FormBinding binding = new FormBinding(request);
        request.setAttribute(ATTRIBUTE, binding);
        return binding;
    }

    /**
     * Check that an argument can read the fields of the form by name, through the
     * {@link FormRouteCompleter}: called when the completer of a request is created.
     *
     * @param request The request, with a form body
     * @throws IllegalStateException if the form is read whole by a {@link FormData} or a
     *                               {@link FormParts} argument
     */
    public static void checkFieldsCanBeRead(FormCapableHttpRequest<?> request) {
        FormBinding binding = of(request);
        synchronized (binding) {
            if (binding.mode != Mode.FIELDS) {
                throw binding.refused("an argument that reads a form field by name");
            }
        }
    }

    /**
     * Whether an argument is one of the types {@link #bind} binds.
     *
     * @param argument The argument
     * @return Whether the argument is bound by {@link #bind}: a {@link FileUpload}, a
     * {@code List<FileUpload>}, a {@link FormPart}, or an {@code Optional} of one of them
     */
    public static boolean isBound(Argument<?> argument) {
        return Kind.of(argument) != null;
    }

    /**
     * Bind a {@link FileUpload}, a {@code List<FileUpload>}, a {@link FormPart}, or an
     * {@code Optional} of one of them, by the name of its {@link Part} or its own name.
     *
     * @param context           The conversion context of the argument
     * @param request           The request, with a form body
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @param <T>               The type of the argument
     * @return The binding result, pending until the argument is available
     */
    public static <T> ArgumentBinder.BindingResult<T> bind(ArgumentConversionContext<T> context,
                                                           FormCapableHttpRequest<?> request,
                                                           FormFactory factory,
                                                           ConversionService conversionService) {
        Argument<T> argument = context.getArgument();
        Kind kind = Kind.of(argument);
        if (kind == null) {
            return ArgumentBinder.BindingResult.unsatisfied();
        }
        String name = name(argument);
        FormBinding binding = of(request);
        Mode mode;
        synchronized (binding) {
            mode = binding.mode;
        }
        return switch (mode) {
            case FIELDS -> kind == Kind.PART
                ? binding.streamPart(factory, argument, name)
                : binding.storeFiles(factory, argument, name, kind == Kind.FILES);
            case COLLECTED -> {
                if (kind == Kind.PART) {
                    throw binding.refused("the argument [" + argument + "], which streams its part");
                }
                yield pending(request, map(binding.form(factory, conversionService), form -> kind == Kind.FILES ? files(form, name) : file(form, name)));
            }
            case STREAMED, REFUSED -> throw binding.refused("the argument [" + argument + "]");
        };
    }

    /**
     * Bind a form field by name when the form is read whole: from the {@link FormData} of the
     * route, or not at all when the route streams it with {@link FormParts}.
     *
     * @param conversionService The conversion service
     * @param context           The conversion context of the argument
     * @param request           The request, with a form body
     * @param factory           The form factory
     * @param name              The name of the field
     * @param <T>               The type of the argument
     * @return The binding result, or {@code null} if the field is read by name through the
     * {@link FormRouteCompleter}
     */
    public static <T> ArgumentBinder.@Nullable BindingResult<T> bindField(ConversionService conversionService,
                                                                          ArgumentConversionContext<T> context,
                                                                          FormCapableHttpRequest<?> request,
                                                                          FormFactory factory,
                                                                          String name) {
        FormBinding binding = of(request);
        Mode mode;
        synchronized (binding) {
            mode = binding.mode;
        }
        Argument<T> argument = context.getArgument();
        return switch (mode) {
            case FIELDS -> null;
            case COLLECTED -> {
                CompletableFuture<Optional<T>> value = map(binding.form(factory, conversionService), form -> convert(conversionService, context, form.getValues(name)));
                BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(value));
                yield new PendingRequestBindingResult<>() {
                    @Override
                    public boolean isPending() {
                        return !value.isDone();
                    }

                    @Override
                    public Optional<T> getValue() {
                        Optional<T> result = value.getNow(Optional.empty());
                        return result == null ? Optional.empty() : result;
                    }

                    @Override
                    public List<ConversionError> getConversionErrors() {
                        return context.getLastError().map(List::of).orElseGet(List::of);
                    }
                };
            }
            case STREAMED, REFUSED -> {
                if (argument.getAnnotationMetadata().hasAnnotation(Part.class)) {
                    throw binding.refused("the argument [" + argument + "]");
                }
                // not a form field: it may be bound from elsewhere, or be missing
                yield ArgumentBinder.BindingResult.unsatisfied();
            }
        };
    }

    /**
     * The form of a {@link FormData} argument: read once for the request, and shared by the
     * arguments that are taken from it.
     *
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @return Completes with the form
     */
    CompletableFuture<FormData> form(FormFactory factory, ConversionService conversionService) {
        CompletableFuture<FormData> collected;
        synchronized (this) {
            if (form != null) {
                return form;
            }
            if (mode == Mode.FIELDS && FormFactory.getCompleterOrNull(request) == null) {
                // a FormData the route does not declare, such as the member of a request bean
                mode = Mode.COLLECTED;
                reader = "a FormData";
            }
            if (mode != Mode.COLLECTED) {
                throw refused("a FormData");
            }
            collected = FormDataArgumentBinder.collect(factory, conversionService, request);
            form = collected;
        }
        BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(collected));
        return collected;
    }

    /**
     * The {@link FormParts} of a request: one for the request, shared by its arguments.
     *
     * @param factory The form factory
     * @return The parts
     */
    FormParts parts(FormFactory factory) {
        synchronized (this) {
            if (parts != null) {
                return parts;
            }
            if (mode == Mode.FIELDS && FormFactory.getCompleterOrNull(request) == null) {
                mode = Mode.STREAMED;
                reader = "FormParts";
            }
            if (mode != Mode.STREAMED) {
                throw refused("FormParts");
            }
            DefaultFormParts created = new DefaultFormParts(request, UploadContext.of(factory, request));
            // the handler closes the parts when it completes; this is the safety net for handlers
            // that do not, failures before the handler is called, and disconnects
            request.addDisposalResource(created::close);
            parts = created;
            return created;
        }
    }

    private <T> ArgumentBinder.BindingResult<T> storeFiles(FormFactory factory, Argument<T> argument, String name, boolean all) {
        UploadContext context = UploadContext.of(factory, request);
        Flux<FileUpload> stored = Flux.from(factory.getOrCreateCompleter(request)
                .subscribeField(name, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_FULL, argument)))
            // the files of a name arrive one after the other: each one is stored before the next
            .concatMap(field -> ReactiveExecutionFlow.toPublisher(store(factory, context, field)));
        CompletableFuture<?> value = all
            // no file of the name leaves the argument unsatisfied, like a single file
            ? stored.collectList().filter(files -> !files.isEmpty()).toFuture()
            // like a CompletedFileUpload: the first file of the name, the others are discarded
            : stored.next().toFuture();
        return pending(request, value);
    }

    private ExecutionFlow<FileUpload> store(FormFactory factory, UploadContext context, RawFormField field) {
        // stored with the limits of the multipart configuration, and refused with 400 when it is
        // not a file, like a CompletedFileUpload
        return factory.completeFileUpload(request, field).map(upload -> {
            // the request disposes of the upload it completed: this takes over the content, and
            // releases what the application did not consume when the request ends
            FileUpload file = new DefaultFileUpload(new StoredUploadContent(upload.moveResource(), context));
            request.addDisposalResource(() -> release(file));
            return file;
        });
    }

    private <T> ArgumentBinder.BindingResult<T> streamPart(FormFactory factory, Argument<T> argument, String name) {
        UploadContext context = UploadContext.of(factory, request);
        // like a StreamingFileUpload: the route runs once the part starts, and the application
        // reads its content as it arrives
        CompletableFuture<FormPart> value = Flux.from(factory.getOrCreateCompleter(request)
                .subscribeField(name, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_START, argument)))
            .next()
            .map(field -> {
                FormPart part = new DefaultFormPart(new StreamingUploadContent(field, context));
                request.addDisposalResource(part::close);
                return part;
            })
            .toFuture();
        return pending(request, value);
    }

    private IllegalStateException refused(String what) {
        String message;
        if (mode == Mode.REFUSED) {
            message = Objects.requireNonNull(conflict);
        } else if (mode == Mode.STREAMED) {
            message = "The form of the request is streamed by [" + reader + "], so " + what
                + " cannot read it as well: read every field from the FormParts";
        } else if (mode == Mode.COLLECTED) {
            message = "The form of the request is read whole by [" + reader + "], so " + what
                + " cannot read it as well: bind files as FileUpload and text fields by name, which are taken from the same form, or read them from the FormData";
        } else {
            message = "The fields of the form of the request are read by name by other arguments, so " + what
                + " cannot read the whole form: bind files as FileUpload and text fields by name";
        }
        return new IllegalStateException(message);
    }

    private static @Nullable FileUpload file(FormData form, String name) {
        Optional<FileUpload> file = form.findFile(name);
        if (file.isPresent()) {
            return file.get();
        }
        checkNotText(form, name);
        return null;
    }

    private static @Nullable List<FileUpload> files(FormData form, String name) {
        List<FileUpload> files = form.getFiles(name);
        if (!files.isEmpty()) {
            return files;
        }
        checkNotText(form, name);
        return null;
    }

    private static void checkNotText(FormData form, String name) {
        if (form.contains(name)) {
            // a text field where a file was expected, answered like a CompletedFileUpload
            throw FormFieldException.notAFile(name);
        }
    }

    private static <T> Optional<T> convert(ConversionService conversionService, ArgumentConversionContext<T> context, List<String> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        Class<T> type = context.getArgument().getType();
        boolean many = Iterable.class.isAssignableFrom(type) || type.isArray();
        // a single value is the first field of the name, like a field read by name
        return conversionService.convert(many ? values : values.get(0), context);
    }

    private static String name(Argument<?> argument) {
        return argument.getAnnotationMetadata().stringValue(Bindable.NAME).orElse(argument.getName());
    }

    private static void release(FileUpload file) {
        file.closeAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                LOG.warn("Failed to release the uploaded file {}", file.name(), error);
            }
        });
    }

    /**
     * A future that completes with the result of the function, or its failure, not wrapped.
     */
    private static <R, V> CompletableFuture<V> map(CompletableFuture<R> future, Function<? super R, ? extends @Nullable V> function) {
        CompletableFuture<V> result = new CompletableFuture<>();
        // completes result: nothing to wait for on the returned stage
        var ignored = future.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
                return;
            }
            try {
                result.complete(function.apply(value));
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> ArgumentBinder.BindingResult<T> pending(HttpRequest<?> request, CompletableFuture<?> value) {
        BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(value));
        return new PendingRequestBindingResult<>() {
            @Override
            public boolean isPending() {
                return !value.isDone();
            }

            @Override
            public Optional<T> getValue() {
                return Optional.ofNullable((T) value.getNow(null));
            }
        };
    }

    private enum Mode {
        FIELDS,
        COLLECTED,
        STREAMED,
        REFUSED
    }

    /**
     * The types {@link #bind} binds.
     */
    private enum Kind {
        FILE,
        FILES,
        PART;

        static @Nullable Kind of(Argument<?> argument) {
            Class<?> type = argument.getType();
            if (type == Optional.class) {
                Argument<?> element = argument.getFirstTypeVariable().orElse(null);
                if (element == null) {
                    return null;
                }
                type = element.getType();
                return type == FileUpload.class ? FILE : type == FormPart.class ? PART : null;
            }
            if (type == FileUpload.class) {
                return FILE;
            }
            if (type == FormPart.class) {
                return PART;
            }
            if (type == List.class && argument.getFirstTypeVariable().map(Argument::getType).orElse(null) == FileUpload.class) {
                return FILES;
            }
            return null;
        }
    }
}
