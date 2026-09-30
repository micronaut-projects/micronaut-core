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
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.CompletableFutureExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.InternalByteBody;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private static final String ARGUMENT = "the argument [";

    private final FormCapableHttpRequest<?> request;
    /**
     * The arguments of the route: the others, e.g. those of a filter method, read the whole form.
     */
    private final Argument<?>[] routeArguments;
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
        routeArguments = route instanceof MethodBasedRouteMatch<?, ?> m ? m.getArguments() : new Argument<?>[0];
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
        // a read of an AsyncRequestBody consumed the body, even AsyncRequestBody.form(), e.g. in a
        // filter: the form is not read for anything that comes after it
        String consumed = InternalByteBody.claimDescription(request.byteBody());
        if (consumed != null) {
            throw new IllegalStateException(consumed);
        }
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
     * @param source            The request the argument is bound from, e.g. one a filter continued with
     * @param request           The request, with a form body
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @param <T>               The type of the argument
     * @return The binding result, pending until the argument is available
     */
    @SuppressWarnings("unchecked")
    public static <T> ArgumentBinder.BindingResult<T> bind(ArgumentConversionContext<T> context,
                                                           HttpRequest<?> source,
                                                           FormCapableHttpRequest<?> request,
                                                           FormFactory factory,
                                                           ConversionService conversionService) {
        Argument<T> argument = context.getArgument();
        Kind kind = Kind.of(argument);
        if (kind == null) {
            return ArgumentBinder.BindingResult.unsatisfied();
        }
        String name = name(argument);
        CompletableFuture<@Nullable FormData> replaced = replacedForm(source, conversionService);
        if (replaced != null) {
            // a filter set the body: the files are those of the form it set, never the bytes
            FormData form = replaced.getNow(null);
            if (form == null || kind == Kind.PART) {
                return ArgumentBinder.BindingResult.unsatisfied();
            }
            Object value = kind == Kind.FILES ? files(form, name) : file(form, name);
            return () -> Optional.ofNullable((T) value);
        }
        FormBinding binding = of(request);
        if (kind == Kind.PART && !binding.isRouteArgument(argument)) {
            // e.g. an argument of a filter method: like its FormParts, it streams its own part
            // and consumes the body
            return binding.consumingPart(factory, argument, name);
        }
        Mode mode;
        synchronized (binding) {
            mode = binding.mode;
        }
        if (mode == Mode.FIELDS && !binding.isRouteArgument(argument)) {
            // e.g. an argument of a filter method, which runs before the fields are read by name:
            // it reads the whole form, which the arguments of the route share
            mode = Mode.COLLECTED;
        }
        return switch (mode) {
            case FIELDS -> kind == Kind.PART
                ? binding.streamPart(factory, source, argument, name)
                : binding.storeFiles(factory, argument, name, kind == Kind.FILES);
            case COLLECTED -> {
                if (kind == Kind.PART) {
                    throw binding.refused(ARGUMENT + argument + "], which streams its part");
                }
                yield pending(request, map(binding.form(factory, conversionService), form -> kind == Kind.FILES ? files(form, name) : file(form, name)));
            }
            case STREAMED, REFUSED -> throw binding.refused(ARGUMENT + argument + "]");
        };
    }

    /**
     * Bind a form field by name when the form is read whole: from the {@link FormData} of the
     * route, or not at all when the route streams it with {@link FormParts}.
     *
     * @param conversionService The conversion service
     * @param context           The conversion context of the argument
     * @param source            The request the argument is bound from, e.g. one a filter continued with
     * @param request           The request, with a form body
     * @param factory           The form factory
     * @param name              The name of the field
     * @param <T>               The type of the argument
     * @return The binding result, or {@code null} if the field is read by name through the
     * {@link FormRouteCompleter}
     */
    public static <T> ArgumentBinder.@Nullable BindingResult<T> bindField(ConversionService conversionService,
                                                                          ArgumentConversionContext<T> context,
                                                                          HttpRequest<?> source,
                                                                          FormCapableHttpRequest<?> request,
                                                                          FormFactory factory,
                                                                          String name) {
        CompletableFuture<@Nullable FormData> replaced = replacedForm(source, conversionService);
        if (replaced != null) {
            // a filter set the body: the fields are those of the form it set, never the bytes
            FormData form = replaced.getNow(null);
            if (form == null) {
                return ArgumentBinder.BindingResult.unsatisfied();
            }
            Optional<T> value = convert(conversionService, context, form.getValues(name));
            return new ArgumentBinder.BindingResult<>() {
                @Override
                public Optional<T> getValue() {
                    return value;
                }

                @Override
                public List<ConversionError> getConversionErrors() {
                    return context.getLastError().map(List::of).orElseGet(List::of);
                }
            };
        }
        FormBinding binding = of(request);
        Mode mode;
        synchronized (binding) {
            mode = binding.mode;
        }
        Argument<T> argument = context.getArgument();
        if (mode == Mode.FIELDS && !binding.isRouteArgument(argument)) {
            // e.g. an argument of a filter method, which runs before the fields are read by name:
            // it reads the whole form, which the arguments of the route share
            mode = Mode.COLLECTED;
        }
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
                        return value.getNow(Optional.empty());
                    }

                    @Override
                    public List<ConversionError> getConversionErrors() {
                        return context.getLastError().map(List::of).orElseGet(List::of);
                    }
                };
            }
            case STREAMED, REFUSED -> {
                if (argument.getAnnotationMetadata().hasAnnotation(Part.class)) {
                    throw binding.refused(ARGUMENT + argument + "]");
                }
                // not a form field: it may be bound from elsewhere, or be missing
                yield ArgumentBinder.BindingResult.unsatisfied();
            }
        };
    }

    /**
     * The form of a request whose body a filter set, see {@link ServerRequestBody#isBodySet}: a
     * body set to {@code null} is a form without fields, like no body, and a body set to an object
     * is that form, not the bytes of the request, which are never read then.
     *
     * @param request           The request
     * @param conversionService The conversion service
     * @return {@code null} if no filter set the body, else a completed future with the form, or
     * with {@code null} if the body that was set is not a form
     */
    static @Nullable CompletableFuture<@Nullable FormData> replacedForm(HttpRequest<?> request, ConversionService conversionService) {
        if (!ServerRequestBody.isBodySet(request)) {
            return null;
        }
        Object body = request.getBody().orElse(null);
        if (body == null) {
            return CompletableFuture.completedFuture(new DefaultFormData(Map.of(), Map.of(), conversionService));
        }
        FormData form = body instanceof FormData data ? data : conversionService.convert(body, FormData.class).orElse(null);
        return CompletableFuture.completedFuture(form);
    }

    /**
     * Bind an argument that is taken from the form of a request whose body a filter set, see
     * {@link ServerRequestBody#isBodySet}: a {@link FileUpload}, a {@code List<FileUpload>}, an
     * {@code Optional} of one, or a text field by name. A body set to {@code null} is a form without
     * fields, and a body set to a {@link FormData} (or an object that converts to one) is that form;
     * the bytes of the request are never read. A {@link FormPart} cannot be streamed from it.
     *
     * @param context           The conversion context of the argument
     * @param source            The request the argument is bound from
     * @param conversionService The conversion service
     * @param name              The name of the field
     * @param <T>               The type of the argument
     * @return The binding result, or {@code null} if no filter set the body of a form request
     */
    @SuppressWarnings("unchecked")
    public static <T> ArgumentBinder.@Nullable BindingResult<T> bindReplaced(ArgumentConversionContext<T> context,
                                                                             HttpRequest<?> source,
                                                                             ConversionService conversionService,
                                                                             String name) {
        if (!isForm(source)) {
            return null;
        }
        CompletableFuture<@Nullable FormData> replaced = replacedForm(source, conversionService);
        if (replaced == null) {
            return null;
        }
        FormData form = replaced.getNow(null);
        if (form == null) {
            return ArgumentBinder.BindingResult.unsatisfied();
        }
        Kind kind = Kind.of(context.getArgument());
        if (kind == Kind.PART) {
            return ArgumentBinder.BindingResult.unsatisfied();
        }
        if (kind != null) {
            Object value = kind == Kind.FILES ? files(form, name) : file(form, name);
            return () -> Optional.ofNullable((T) value);
        }
        Optional<T> value = convert(conversionService, context, form.getValues(name));
        return new ArgumentBinder.BindingResult<>() {
            @Override
            public Optional<T> getValue() {
                return value;
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }
        };
    }

    /**
     * @param request A request
     * @return Whether its content type is a form, {@code application/x-www-form-urlencoded} or
     * {@code multipart/form-data}
     */
    static boolean isForm(HttpRequest<?> request) {
        MediaType contentType = request.getContentType().orElse(null);
        return contentType != null
            && (contentType.matches(MediaType.APPLICATION_FORM_URLENCODED_TYPE) || contentType.matches(MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    /**
     * @param argument An argument
     * @return Whether it is an argument of the route of the request
     */
    private boolean isRouteArgument(Argument<?> argument) {
        for (Argument<?> routeArgument : routeArguments) {
            if (routeArgument == argument) {
                return true;
            }
        }
        return false;
    }

    /**
     * The form request of a request: the request itself, or the server request it wraps, e.g.
     * the request a filter continued with.
     *
     * @param request The request
     * @return The form request, or {@code null} if the request has no form body
     */
    public static @Nullable FormCapableHttpRequest<?> formRequest(HttpRequest<?> request) {
        FormCapableHttpRequest<?> form = null;
        if (request instanceof FormCapableHttpRequest<?> f) {
            form = f;
        } else if (ServerRequestBody.of(request) instanceof FormCapableHttpRequest<?> f) {
            form = f;
        }
        return form != null && form.hasFormBody() ? form : null;
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
        return form(factory, conversionService, request::getRawFormFields);
    }

    /**
     * The form of the request, like {@link #form(FormFactory, ConversionService)}, read from the
     * given fields if it was not started before: e.g. those of a split of the bytes of the
     * request, for a copy of the body, which leaves the bytes to the other readers of the request.
     * The form is the one of the request either way, shared by the arguments that are taken from it.
     *
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @param fields            The fields the form is read from, asked for if this call starts the form
     * @return Completes with the form
     */
    CompletableFuture<FormData> form(FormFactory factory, ConversionService conversionService, Supplier<Publisher<RawFormField>> fields) {
        startForm(factory, conversionService, fields, true);
        synchronized (this) {
            return Objects.requireNonNull(form, "form");
        }
    }

    /**
     * Start the form of the request, like {@link #form}, unless it was started before.
     *
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @return The collection this call started, which no other argument shares yet, or
     * {@code null} if the form was started before: it is shared, see {@link #form}
     */
    FormDataArgumentBinder.@Nullable Collection startForm(FormFactory factory, ConversionService conversionService) {
        return startForm(factory, conversionService, request::getRawFormFields, true);
    }

    /**
     * Start the form of the request, like {@link #startForm(FormFactory, ConversionService)},
     * outside the argument binding of the route: the route does not wait for the form, the caller
     * does, e.g. a handler that reads the form while it runs.
     *
     * @param factory           The form factory
     * @param conversionService The conversion service
     * @return The collection this call started, or {@code null} if the form was started before
     */
    FormDataArgumentBinder.@Nullable Collection startDetachedForm(FormFactory factory, ConversionService conversionService) {
        return startForm(factory, conversionService, request::getRawFormFields, false);
    }

    private FormDataArgumentBinder.@Nullable Collection startForm(FormFactory factory, ConversionService conversionService, Supplier<Publisher<RawFormField>> fields, boolean routeWaits) {
        FormDataArgumentBinder.Collection collection;
        CompletableFuture<FormData> collected;
        synchronized (this) {
            if (form != null) {
                return null;
            }
            if (mode == Mode.FIELDS && FormFactory.getCompleterOrNull(request) == null) {
                // a FormData the route does not declare, such as the member of a request bean
                mode = Mode.COLLECTED;
                reader = "a FormData";
            }
            if (mode != Mode.COLLECTED) {
                throw refused("a FormData");
            }
            collection = FormDataArgumentBinder.start(UploadContext.of(factory, request), factory, conversionService, request, fields);
            collected = collection.result();
            form = collected;
        }
        if (routeWaits) {
            BasicHttpAttributes.addRouteWaitsFor(request, CompletableFutureExecutionFlow.just(collected));
        }
        return collection;
    }

    /**
     * The {@link FormParts} of an argument: for the route, one for the request, shared by its
     * arguments; for another method, e.g. a filter method, parts of its own, which consume the
     * body like {@link io.micronaut.http.body.AsyncRequestBody#parts()}, see {@link #consumingParts}.
     *
     * @param factory  The form factory
     * @param argument The argument
     * @return The parts
     */
    DefaultFormParts parts(FormFactory factory, Argument<?> argument) {
        if (!isRouteArgument(argument)) {
            return consumingParts(factory, argument, "FormParts", "FormParts");
        }
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
            // the route releases the parts when it completed; this is the safety net for failures
            // before the route is invoked, and disconnects
            request.addDisposalResource(created::close);
            parts = created;
            return created;
        }
    }

    /**
     * The {@link FormParts} of an argument of another method than the route, e.g. a filter method:
     * like {@link io.micronaut.http.body.AsyncRequestBody#parts()} in a filter, the parts consume
     * the body once the first part is asked for, and the route, or a later filter, fails when it
     * reads the body, with a message that names the argument. The method releases its parts when
     * it completed.
     *
     * @param factory  The form factory
     * @param argument The argument
     * @param type     The type of the argument, named in the failure of a later read of the body
     * @param what     What reads the form, named when it is refused
     * @return The parts
     */
    private DefaultFormParts consumingParts(FormFactory factory, Argument<?> argument, String type, String what) {
        synchronized (this) {
            if (mode == Mode.COLLECTED || mode == Mode.REFUSED) {
                throw refused(what);
            }
        }
        DefaultFormParts created = new DefaultFormParts(() -> {
            Publisher<RawFormField> fields = request.getRawFormFields();
            // the fields claimed the bytes
            InternalByteBody.describeClaim(request.byteBody(), "The body of the request was already read with the " + type + " argument [" + argument
                + "], e.g. of a filter, which consumes it: read the form with a FormData argument in the filter to leave it for the route");
            return fields;
        }, UploadContext.of(factory, request));
        // the safety net for failures before the method is invoked, and disconnects
        request.addDisposalResource(created::close);
        return created;
    }

    /**
     * The {@link FormPart} of an argument of another method than the route, e.g. a filter method:
     * streamed from parts of its own, see {@link #consumingParts}, which consume the body. The
     * fields before the part are discarded, and the argument is available once the part starts,
     * or missing when the form has no field of the name. The method releases the part when it
     * completed, and the parts with it: the rest of the form is discarded.
     *
     * @param factory  The form factory
     * @param argument The argument
     * @param name     The name of the field
     * @param <T>      The type of the argument
     * @return The binding result, pending until the part starts or the form ended
     */
    private <T> ArgumentBinder.BindingResult<T> consumingPart(FormFactory factory, Argument<T> argument, String name) {
        DefaultFormParts parts = consumingParts(factory, argument, "FormPart", ARGUMENT + argument + "], which streams its part");
        CompletableFuture<@Nullable DefaultFormPart> value = new CompletableFuture<>();
        // the part stays with the method until the parts are released, when the method completed
        CompletableFuture<Void> held = new CompletableFuture<>();
        parts.part(name, part -> {
            value.complete(new DefaultFormPart(((DefaultFormPart) part).content, parts));
            return held;
        }).whenComplete((found, error) -> {
            if (error != null) {
                value.completeExceptionally(error);
            } else if (!Boolean.TRUE.equals(found)) {
                // no field of the name: the argument is missing
                value.complete(null);
            }
        });
        return pending(request, value);
    }

    private <T> ArgumentBinder.BindingResult<T> storeFiles(FormFactory factory, Argument<T> argument, String name, boolean all) {
        UploadContext context = UploadContext.of(factory, request);
        Flux<FileUpload> stored = Flux.from(factory.getOrCreateCompleter(request)
                .subscribeField(name, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_FULL, argument)))
            // the files of a name arrive one after the other: each one is stored before the next
            .concatMap(field -> ReactiveExecutionFlow.toPublisher(store(factory, context, field)))
            // the files waiting behind the one being stored when the argument stops reading
            .doOnDiscard(RawFormField.class, RawFormField::close);
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

    private <T> ArgumentBinder.BindingResult<T> streamPart(FormFactory factory, HttpRequest<?> source, Argument<T> argument, String name) {
        UploadContext context = UploadContext.of(factory, request);
        // like a StreamingFileUpload: the route runs once the part starts, and the application
        // reads its content as it arrives
        CompletableFuture<DefaultFormPart> value = Flux.from(factory.getOrCreateCompleter(request)
                .subscribeField(name, new FormRouteCompleter.SubscriptionMetadata(FormRouteCompleter.SubscriptionMode.WAITS_FOR_START, argument)))
            .next()
            .map(field -> {
                DefaultFormPart part = new DefaultFormPart(new StreamingUploadContent(field, context));
                // what the route did not read is released when the request ends
                request.addDisposalResource(part::close);
                return part;
            })
            .toFuture();
        // like an AsyncRequestBody: a read of the part that is still running when the route
        // completed is aborted, before the response is written, or when its streamed response ended
        BasicHttpAttributes.addRouteBody(source, () -> {
            DefaultFormPart part = value.isDone() && !value.isCompletedExceptionally() ? value.getNow(null) : null;
            return part == null ? CompletableFuture.completedStage(null) : part.releaseBody();
        });
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

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> convert(ConversionService conversionService, ArgumentConversionContext<T> context, List<String> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        Argument<T> argument = context.getArgument();
        boolean many = isMany(argument);
        if (many && argument.getType() == Optional.class) {
            // converted to the collection, with its own context: the conversion to an Optional
            // does not report the values of the collection that were rejected
            Argument<?> valueType = argument.getFirstTypeVariable().orElseThrow();
            ArgumentConversionContext<?> valueContext = ConversionContext.of(
                Argument.of(valueType.getType(), argument.getName(), valueType.getAnnotationMetadata(), valueType.getTypeParameters()),
                context.getLocale(), context.getCharset());
            Optional<?> value = conversionService.convert(values, valueContext);
            ConversionError error = valueContext.getLastError().orElse(null);
            if (error != null) {
                context.reject(error.getOriginalValue().orElse(values), error.getCause());
                return Optional.empty();
            }
            return value.map(v -> (T) Optional.of(v));
        }
        // a single value is the first field of the name, like a field read by name
        Optional<T> converted = conversionService.convert(many ? values : values.get(0), context);
        // a collection is converted from the values that convert, and the others are rejected:
        // that is not the value of the argument, which fails with the error of the conversion
        return context.getLastError().isPresent() ? Optional.empty() : converted;
    }

    /**
     * @param argument The type a text field is converted to
     * @return Whether the type gets every value of the field: a collection or an array, or an
     * {@code Optional} of one
     */
    static boolean isMany(Argument<?> argument) {
        Class<?> type = argument.getType();
        if (type == Optional.class) {
            Argument<?> value = argument.getFirstTypeVariable().orElse(null);
            if (value == null) {
                return false;
            }
            type = value.getType();
        }
        return Iterable.class.isAssignableFrom(type) || type.isArray();
    }

    static String name(Argument<?> argument) {
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
        future.whenComplete((value, error) -> {
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
                if (type == FileUpload.class) {
                    return FILE;
                }
                return type == FormPart.class ? PART : null;
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
