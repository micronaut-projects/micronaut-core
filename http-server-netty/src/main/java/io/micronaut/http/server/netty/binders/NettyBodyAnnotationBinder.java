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
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.InternalByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.context.ServerHttpRequestContext;
import io.micronaut.http.exceptions.HttpException;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.multipart.RawFormField;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration;
import io.micronaut.http.server.netty.converters.NettyConverters;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Internal
final class NettyBodyAnnotationBinder<T> extends DefaultBodyAnnotationBinder<T> {
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String JSON_ALIAS = "com.fasterxml.jackson.annotation.JsonAlias";
    private static final List<String> OWN_DESERIALIZER_ANNOTATIONS = List.of(
        "tools.jackson.databind.annotation.JsonDeserialize",
        "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
        "io.micronaut.serde.annotation.Serdeable$Deserializable"
    );

    final NettyHttpServerConfiguration httpServerConfiguration;
    final MessageBodyHandlerRegistry bodyHandlerRegistry;
    final BeanProvider<FormFactory> formFactory;

    NettyBodyAnnotationBinder(ConversionService conversionService,
                              NettyHttpServerConfiguration httpServerConfiguration,
                              MessageBodyHandlerRegistry bodyHandlerRegistry,
                              BeanProvider<FormFactory> formFactory) {
        super(conversionService);
        this.httpServerConfiguration = httpServerConfiguration;
        this.bodyHandlerRegistry = bodyHandlerRegistry;
        this.formFactory = formFactory;
    }

    @Override
    protected BindingResult<T> bindBodyPart(ArgumentConversionContext<T> context, HttpRequest<?> source, String bodyComponent) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        FormCapableHttpRequest<?> nhr = source instanceof FormCapableHttpRequest<?> formRequest ? formRequest : NettyHttpRequest.findBodyRequest(source);
        if (nhr != null && nhr.hasFormBody()) {
            // skipClaimed=true because for unmatched binding, both this binder and PartUploadAnnotationBinder can be called on the same parameter
            return NettyPartUploadAnnotationBinder.bindPart(conversionService, context, formFactory.get(), nhr, bodyComponent, true);
        } else {
            return super.bindBodyPart(context, source, bodyComponent);
        }
    }

    @Override
    protected BindingResult<ConvertibleValues<?>> bindFullBodyConvertibleValues(HttpRequest<?> source) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        NettyHttpRequest<?> nhr = NettyHttpRequest.findBodyRequest(source);
        if (nhr == null) {
            return super.bindFullBodyConvertibleValues(source);
        }
        BindingResult<ConvertibleValues<?>> existing = nhr.convertibleBody;
        if (existing != null) {
            return existing;
        } else {
            //noinspection unchecked
            BindingResult<ConvertibleValues<?>> result = (BindingResult<ConvertibleValues<?>>) bindFullBody((ArgumentConversionContext<T>) ConversionContext.of(ConvertibleValues.class), nhr);
            nhr.convertibleBody = result;
            return result;
        }
    }

    @Override
    public BindingResult<T> bindFullBody(ArgumentConversionContext<T> context, HttpRequest<?> source) {
        // the request itself, or e.g. the mutable view of the request that a filter continued with
        NettyHttpRequest<?> nhr = NettyHttpRequest.findBodyRequest(source);
        if (nhr == null) {
            return super.bindFullBody(context, source);
        }
        if (nhr.byteBody().expectedLength().orElse(-1) == 0) {
            return bindDefaultValue(context);
        }

        // If there's an error during conversion, the body must stay available, so we split here.
        // This costs us nothing because we need to buffer anyway.
        ByteBody body = nhr.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
        ExecutionFlow<? extends CloseableAvailableByteBody> buffered = InternalByteBody.bufferFlow(body);

        return new PendingRequestBindingResult<>() {
            @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
            @Nullable
            Optional<T> result;

            {
                // NettyRequestLifecycle will "subscribe" to the execution flow added to routeWaitsFor,
                // so we can't subscribe directly ourselves. Instead, use the side effect of a map.
                BasicHttpAttributes.addRouteWaitsFor(nhr, buffered.flatMap(imm ->
                    PropagatedContext.getOrEmpty().plus(new ServerHttpRequestContext(nhr)).propagate(() -> {
                        try {
                            result = transform(nhr, context, imm);
                            return ExecutionFlow.just(null);
                        } catch (Throwable e) {
                            return ExecutionFlow.error(e);
                        }
                    })));
            }

            @SuppressWarnings("OptionalAssignedToNull")
            @Override
            public boolean isPending() {
                return result == null;
            }

            @Override
            public Optional<T> getValue() {
                return result == null ? Optional.empty() : result;
            }

            @Override
            public List<ConversionError> getConversionErrors() {
                return context.getLastError().map(List::of).orElseGet(List::of);
            }
        };
    }

    Optional<T> transform(NettyHttpRequest<?> nhr, ArgumentConversionContext<T> context, AvailableByteBody imm) throws Throwable {
        MessageBodyReader<T> reader = null;
        final RouteInfo<?> routeInfo = RouteAttributes.getRouteInfo(nhr).orElse(null);
        if (routeInfo != null) {
            reader = (MessageBodyReader<T>) routeInfo.getMessageBodyReader();
        }
        MediaType mediaType = nhr.getContentType().orElse(null);
        if (mediaType != null && (reader == null || !reader.isReadable(context.getArgument(), mediaType))) {
            reader = bodyHandlerRegistry.findReader(context.getArgument(), List.of(mediaType)).orElse(null);
        }
        if (reader == null && nhr.hasFormBody()) {
            Map<String, List<CloseableByteBody>> bodies = new LinkedHashMap<>();
            for (RawFormField rff : toListNow(nhr.getRawFormFields(imm))) {
                bodies.computeIfAbsent(rff.metadata().name(), k -> new ArrayList<>(1)).add(rff.byteBody());
            }
            Map<String, Object> intermediate = io.micronaut.http.server.multipart.FormRouteCompleter.mapForGetBody(bodies, nhr.getCharacterEncoding());
            // Use matches() rather than equals() so that this stays consistent with the
            // hasFormBody() check above, which also uses matches() to detect the form type.
            if (mediaType != null && mediaType.matches(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
                removeUnconvertibleEmptyValues(intermediate, context.getArgument());
            }
            Optional<T> converted = conversionService.convert(intermediate, context);
            nhr.setLegacyBody(converted.orElse(null));
            return converted;
        }
        if (reader != null) {
            T result = read(context, reader, nhr.getHeaders(), mediaType, imm.toByteBuffer());
            nhr.setLegacyBody(result);
            return Optional.ofNullable(result);
        }
        ByteBuf byteBuf = NettyByteBodyFactory.toByteBuf(imm);
        Optional<T> converted = conversionService.convert(byteBuf, ByteBuf.class, context.getArgument().getType(), context);
        NettyConverters.postProcess(byteBuf, converted);
        nhr.setLegacyBody(converted.orElse(null));
        return converted;
    }

    /**
     * Removes the empty values of a URL-encoded form that name a bean property an empty string can
     * never convert to, so that they bind as absent. Every other empty value stays: it is a value
     * for a String, and any type outside {@link #rejectsEmptyString(Class)} may accept it.
     *
     * @param form   The form values
     * @param target The body argument
     */
    private static void removeUnconvertibleEmptyValues(Map<String, Object> form, Argument<?> target) {
        if (!form.containsValue(StringUtils.EMPTY_STRING)) {
            return;
        }
        Argument<?> bean = target.isOptional() ? target.getFirstTypeVariable().orElse(target) : target;
        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(bean.getType()).orElse(null);
        if (introspection != null) {
            form.entrySet().removeIf(field -> StringUtils.EMPTY_STRING.equals(field.getValue())
                && namesPropertyRejectingEmptyString(introspection, field.getKey()));
        }
    }

    private static boolean namesPropertyRejectingEmptyString(BeanIntrospection<?> introspection, String field) {
        // JsonConverterRegistrar corrects the field names in the same way before they are bound
        String name = NameUtils.decapitalize(NameUtils.dehyphenate(field));
        // A creator parameter decides over a property of that name, which may be of another type
        for (Argument<?> parameter : introspection.getConstructorArguments()) {
            if (isNamed(parameter, name)) {
                return rejectsEmptyString(parameter.getType()) && !hasOwnDeserializer(parameter);
            }
        }
        for (BeanProperty<?, Object> property : introspection.getBeanProperties()) {
            if (isNamed(property, name)) {
                return rejectsEmptyString(property.getType()) && !hasOwnDeserializer(property);
            }
        }
        return false;
    }

    private static boolean isNamed(AnnotatedElement element, String name) {
        AnnotationMetadata metadata = element.getAnnotationMetadata();
        String renamed = metadata.stringValue(JSON_PROPERTY).orElse(null);
        return name.equals(StringUtils.isEmpty(renamed) ? element.getName() : renamed)
            || List.of(metadata.stringValues(JSON_ALIAS)).contains(name);
    }

    private static boolean hasOwnDeserializer(AnnotationMetadataProvider element) {
        AnnotationMetadata metadata = element.getAnnotationMetadata();
        return OWN_DESERIALIZER_ANNOTATIONS.stream().anyMatch(annotation -> metadata.isPresent(annotation, "using"));
    }

    private static boolean rejectsEmptyString(Class<?> type) {
        if (type.isPrimitive()) {
            return type != char.class;
        }
        if (type.isEnum()) {
            // What an enum declares is only known from its introspection, if it has one
            BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(type).orElse(null);
            return introspection == null
                || (!introspection.getConstructor().getAnnotationMetadata().hasStereotype(Creator.class) && !hasOwnDeserializer(introspection));
        }
        // A type of the JDK has no deserializer of its own, one of the application may have
        return type.getName().startsWith("java.")
            && (Number.class.isAssignableFrom(type)
            || TemporalAccessor.class.isAssignableFrom(type)
            || TemporalAmount.class.isAssignableFrom(type)
            || Date.class.isAssignableFrom(type));
    }

    private static List<RawFormField> toListNow(Flux<RawFormField> flux) {
        var sub = new Subscriber<RawFormField>() {
            final List<RawFormField> list = new ArrayList<>();
            boolean complete = false;
            @Nullable
            Throwable error = null;

            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(RawFormField t) {
                list.add(t);
            }

            @Override
            public void onError(Throwable t) {
                error = t;
                complete = true;
            }

            @Override
            public void onComplete() {
                complete = true;
            }
        };
        flux.subscribe(sub);
        if (!sub.complete) {
            throw new IllegalStateException("Flux did not finish immediately");
        }
        if (sub.error != null) {
            // the fields read before the failure are not bound
            for (RawFormField field : sub.list) {
                field.close();
            }
        }
        if (sub.error instanceof HttpException e) {
            // a malformed form, e.g. a multipart body without its closing boundary, or a form
            // over a limit: answered with its status
            throw e;
        }
        if (sub.error != null) {
            throw new IllegalStateException("Failed to load form fields", sub.error);
        }
        return sub.list;
    }

    @Nullable
    private T read(ArgumentConversionContext<T> context, MessageBodyReader<T> reader, HttpHeaders headers, @Nullable MediaType mediaType, ByteBuffer<?> byteBuffer) {
        boolean success = false;
        try {
            T result = reader.read(context.getArgument(), mediaType, headers, byteBuffer);
            success = true;
            return result;
        } catch (CodecException ce) {
            if (ce.getCause() instanceof Exception e) {
                context.reject(e);
            } else {
                context.reject(ce);
            }
            return null;
        } finally {
            if (!success && byteBuffer instanceof ReferenceCounted rc) {
                rc.release();
            }
        }
    }
}
