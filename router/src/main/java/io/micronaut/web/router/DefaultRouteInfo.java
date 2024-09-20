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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.sse.Event;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.scheduling.executor.ThreadSelection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * The default route info implementation.
 *
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public class DefaultRouteInfo<R> implements RouteInfo<R> {

    protected final ReturnType<? extends R> returnType;
    protected final List<MediaType> consumesMediaTypes;
    protected final List<MediaType> producesMediaTypes;
    protected final AnnotationMetadata annotationMetadata;
    protected final Class<?> declaringType;
    protected final boolean consumesMediaTypesContainsAll;
    protected final boolean producesMediaTypesContainsAll;
    @Nullable
    protected final HttpStatus definedStatus;
    protected final boolean isWebSocketRoute;
    private final boolean isVoid;
    private final boolean imperative;
    private final boolean suspended;
    private final boolean reactive;
    private final boolean single;
    private final boolean async;
    private final boolean completable;
    private final boolean specifiedSingle;
    private final boolean asyncOrReactive;
    private final Argument<?> bodyType;
    private final boolean isErrorRoute;
    private final boolean isPermitsBody;
    private final MessageBodyWriter<R> messageBodyWriter;

    public DefaultRouteInfo(ReturnType<? extends R> returnType,
                            Class<?> declaringType,
                            boolean isErrorRoute,
                            boolean isPermitsBody) {
        this(AnnotationMetadata.EMPTY_METADATA, returnType, List.of(), List.of(), declaringType, isErrorRoute, isPermitsBody, MessageBodyHandlerRegistry.EMPTY);
    }

    public DefaultRouteInfo(AnnotationMetadata annotationMetadata,
                            ReturnType<? extends R> returnType,
                            List<MediaType> consumesMediaTypes,
                            List<MediaType> producesMediaTypes,
                            Class<?> declaringType,
                            boolean isErrorRoute,
                            boolean isPermitsBody,
                            MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        this.annotationMetadata = annotationMetadata;
        this.returnType = returnType;
        this.bodyType = resolveBodyType(returnType);
        var argBodyType = (Argument<R>) bodyType;
        this.messageBodyWriter = messageBodyHandlerRegistry.findWriter(argBodyType, producesMediaTypes)
            .map(w -> w.createSpecific(argBodyType))
            .orElse(null);
        single = returnType.isSingleResult() ||
            (isReactive() && returnType.getFirstTypeVariable()
                .filter(t -> HttpResponse.class.isAssignableFrom(t.getType())).isPresent()) ||
            returnType.isAsync() ||
            returnType.isSuspended();
        specifiedSingle = returnType.isSpecifiedSingle();
        completable = returnType.isCompletable();
        async = returnType.isAsync();
        asyncOrReactive = returnType.isAsyncOrReactive();
        reactive = returnType.isReactive();
        suspended = returnType.isSuspended();
        this.declaringType = declaringType;
        this.isErrorRoute = isErrorRoute;
        this.isPermitsBody = isPermitsBody;
        this.isVoid = returnType.isVoid();
        isWebSocketRoute = annotationMetadata.hasAnnotation("io.micronaut.websocket.annotation.OnMessage");
        definedStatus = annotationMetadata.enumValue(Status.class, HttpStatus.class).orElse(null);

        if (producesMediaTypes.isEmpty()) {
            MediaType[] producesTypes = MediaType.of(annotationMetadata.stringValues(Produces.class));
            Optional<Argument<?>> firstTypeVariable = returnType.getFirstTypeVariable();
            if (firstTypeVariable.isPresent() && Event.class.isAssignableFrom(firstTypeVariable.get().getType())) {
                this.producesMediaTypes = List.of(MediaType.TEXT_EVENT_STREAM_TYPE);
                producesMediaTypesContainsAll = true;
            } else if (ArrayUtils.isNotEmpty(producesTypes)) {
                this.producesMediaTypes = List.of(producesTypes);
                producesMediaTypesContainsAll = this.producesMediaTypes.contains(MediaType.ALL_TYPE);
            } else {
                producesMediaTypesContainsAll = true;
                this.producesMediaTypes = RouteInfo.DEFAULT_PRODUCES;
            }
        } else {
            this.producesMediaTypes = producesMediaTypes;
            producesMediaTypesContainsAll = this.producesMediaTypes.contains(MediaType.ALL_TYPE);
        }

        if (consumesMediaTypes.isEmpty()) {
            MediaType[] consumesTypes = MediaType.of(annotationMetadata.stringValues(Consumes.class));
            if (ArrayUtils.isNotEmpty(consumesTypes)) {
                this.consumesMediaTypes = List.of(consumesTypes);
                consumesMediaTypesContainsAll = this.consumesMediaTypes.contains(MediaType.ALL_TYPE);
            } else {
                this.consumesMediaTypes = List.of();
                consumesMediaTypesContainsAll = true;
            }
        } else {
            this.consumesMediaTypes = consumesMediaTypes;
            consumesMediaTypesContainsAll = this.consumesMediaTypes.contains(MediaType.ALL_TYPE);
        }
        this.imperative =
            (returnType.getType() == void.class && !suspended)
            || !suspended
            && !reactive
            && !async
            && !returnType.getType().equals(Object.class)
            && (returnType.getType().getPackageName().startsWith("java.") || BeanIntrospector.SHARED.findIntrospection(returnType.getType()).isPresent());
    }

    @Override
    public MessageBodyWriter<R> getMessageBodyWriter() {
        return messageBodyWriter;
    }

    private static Argument<?> resolveBodyType(ReturnType<?> returnType) {
        if (returnType.isAsyncOrReactive()) {
            Argument<?> unwrappedType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (HttpResponse.class.isAssignableFrom(unwrappedType.getType())) {
                unwrappedType = unwrappedType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return appendAnnotations(returnType, unwrappedType);
        } else if (HttpResponse.class.isAssignableFrom(returnType.getType())) {
            Argument<?> unwrappedType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            if (unwrappedType.isAsyncOrReactive()) {
                unwrappedType = unwrappedType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            }
            return appendAnnotations(returnType, unwrappedType);
        } else if (returnType.isOptional()) {
            Argument<?> unwrappedType = returnType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            return appendAnnotations(returnType, unwrappedType);
        }
        return returnType.asArgument();
    }

    private static Argument<?> appendAnnotations(ReturnType<?> returnType, Argument<?> unwrappedType) {
        if (unwrappedType.getAnnotationMetadata().isEmpty()) {
            return unwrappedType.withAnnotationMetadata(returnType.getAnnotationMetadata());
        }
        MutableAnnotationMetadata mutableAnnotationMetadata = new MutableAnnotationMetadata();
        mutableAnnotationMetadata.addAnnotationMetadata(MutableAnnotationMetadata.of(unwrappedType.getAnnotationMetadata()));
        mutableAnnotationMetadata.addAnnotationMetadata(MutableAnnotationMetadata.of(returnType.getAnnotationMetadata()));
        return unwrappedType.withAnnotationMetadata(mutableAnnotationMetadata);
    }

    @Override
    public Optional<Argument<?>> getRequestBodyType() {
        return Optional.empty();
    }

    @Override
    public ReturnType<? extends R> getReturnType() {
        return returnType;
    }

    @Override
    @NonNull
    public Argument<?> getResponseBodyType() {
        return bodyType;
    }

    @Override
    public Class<?> getDeclaringType() {
        return declaringType;
    }

    @Override
    public List<MediaType> getProduces() {
        return producesMediaTypes;
    }

    @Override
    public List<MediaType> getConsumes() {
        return consumesMediaTypes;
    }

    @Override
    public boolean consumesAll() {
        return consumesMediaTypesContainsAll;
    }

    @Override
    public boolean doesConsume(MediaType contentType) {
        return contentType == null || consumesMediaTypesContainsAll || explicitlyConsumes(contentType);
    }

    @Override
    public boolean producesAll() {
        return producesMediaTypesContainsAll;
    }

    @Override
    public boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes) {
        return producesMediaTypesContainsAll || anyMediaTypesMatch(producesMediaTypes, acceptableTypes);
    }

    @Override
    public boolean doesProduce(@Nullable MediaType acceptableType) {
        return producesMediaTypesContainsAll || acceptableType == null || acceptableType.equals(MediaType.ALL_TYPE) || producesMediaTypes.contains(acceptableType);
    }

    private boolean anyMediaTypesMatch(List<MediaType> producedMediaTypes, Collection<MediaType> acceptableTypes) {
        if (CollectionUtils.isEmpty(acceptableTypes)) {
            return true;
        }
        for (MediaType acceptableType : acceptableTypes) {
            if (acceptableType.equals(MediaType.ALL_TYPE) || producedMediaTypes.contains(acceptableType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean explicitlyConsumes(MediaType contentType) {
        return consumesMediaTypes.contains(contentType);
    }

    @Override
    public boolean explicitlyProduces(MediaType contentType) {
        return producesMediaTypes == null || producesMediaTypes.isEmpty() || producesMediaTypes.contains(contentType);
    }

    @Override
    public boolean isSuspended() {
        return suspended;
    }

    @Override
    public boolean isImperative() {
        return imperative;
    }

    @Override
    public boolean isReactive() {
        return reactive;
    }

    @Override
    public boolean isSingleResult() {
        return single;
    }

    @Override
    public boolean isSpecifiedSingle() {
        return specifiedSingle;
    }

    @Override
    public boolean isCompletable() {
        return completable;
    }

    @Override
    public boolean isAsync() {
        return async;
    }

    @Override
    public boolean isAsyncOrReactive() {
        return asyncOrReactive;
    }

    @Override
    public boolean isVoid() {
        return isVoid;
    }

    @Override
    @NonNull
    public HttpStatus findStatus(HttpStatus defaultStatus) {
        if (definedStatus != null) {
            return definedStatus;
        }
        if (defaultStatus != null) {
            return defaultStatus;
        }
        return HttpStatus.OK;
    }

    @Override
    public boolean isErrorRoute() {
        return isErrorRoute;
    }

    @Override
    public boolean isWebSocketRoute() {
        return isWebSocketRoute;
    }

    @Override
    public boolean isPermitsRequestBody() {
        return isPermitsBody;
    }

    @Override
    public ExecutorService getExecutor(ThreadSelection threadSelection) {
        return null;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean needsRequestBody() {
        return isPermitsBody;
    }
}
