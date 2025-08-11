/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.bind;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.PushCapableHttpRequest;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.http.bind.binders.ContinuationArgumentBinder;
import io.micronaut.http.bind.binders.CookieAnnotationBinder;
import io.micronaut.http.bind.binders.CookieObjectArgumentBinder;
import io.micronaut.http.bind.binders.DefaultBodyAnnotationBinder;
import io.micronaut.http.bind.binders.DefaultUnmatchedRequestArgumentBinder;
import io.micronaut.http.bind.binders.HeaderAnnotationBinder;
import io.micronaut.http.bind.binders.PartAnnotationBinder;
import io.micronaut.http.bind.binders.PathVariableAnnotationBinder;
import io.micronaut.http.bind.binders.PendingRequestBindingResult;
import io.micronaut.http.bind.binders.QueryValueArgumentBinder;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.RequestAttributeAnnotationBinder;
import io.micronaut.http.bind.binders.RequestBeanAnnotationBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.core.util.KotlinUtils.KOTLIN_COROUTINES_SUPPORTED;

/**
 * Default implementation of the {@link RequestBinderRegistry} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRequestBinderRegistry implements RequestBinderRegistry {

    private static final long CACHE_MAX_SIZE = 30;

    private final Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation = new LinkedHashMap<>();
    private final Map<TypeAndAnnotation, RequestArgumentBinder> byTypeAndAnnotation = new LinkedHashMap<>();
    private final Map<Integer, RequestArgumentBinder> byType = new LinkedHashMap<>();
    private final ConversionService conversionService;
    private final Map<TypeAndAnnotation, Optional<RequestArgumentBinder>> argumentBinderCache =
        new ConcurrentLinkedHashMap.Builder<TypeAndAnnotation, Optional<RequestArgumentBinder>>().maximumWeightedCapacity(CACHE_MAX_SIZE).build();
    private final List<RequestArgumentBinder<Object>> unmatchedBinders = new ArrayList<>();
    private final DefaultUnmatchedRequestArgumentBinder defaultUnmatchedRequestArgumentBinder;

    /**
     * @param conversionService The conversion service
     * @param binders           The request argument binders
     */
    public DefaultRequestBinderRegistry(ConversionService conversionService, RequestArgumentBinder... binders) {
        this(conversionService, Arrays.asList(binders));
    }

    public DefaultRequestBinderRegistry(ConversionService conversionService, List<RequestArgumentBinder> binders) {
        this(conversionService, binders, new DefaultBodyAnnotationBinder(conversionService));
    }

    /**
     * @param conversionService    The conversion service
     * @param binders              The request argument binders
     * @param bodyAnnotationBinder The body annotation binder
     */
    @Inject
    public DefaultRequestBinderRegistry(
        ConversionService conversionService,
        List<RequestArgumentBinder> binders,
        DefaultBodyAnnotationBinder bodyAnnotationBinder
    ) {
        this.conversionService = conversionService;
        if (CollectionUtils.isNotEmpty(binders)) {
            for (RequestArgumentBinder binder : binders) {
                addArgumentBinder(binder);
            }
        }

        byAnnotation.put(Body.class, bodyAnnotationBinder);
        registerDefaultAnnotationBinders(byAnnotation);

        byType.put(Argument.of(HttpHeaders.class).typeHashCode(), (RequestArgumentBinder<HttpHeaders>) (argument, source) -> () -> Optional.of(source.getHeaders()));
        byType.put(Argument.of(HttpRequest.class).typeHashCode(), (RequestArgumentBinder<HttpRequest<?>>) (argument, source) -> convertBodyIfNecessary(bodyAnnotationBinder, argument, source, RequestType.STANDARD));
        byType.put(Argument.of(ServerHttpRequest.class).typeHashCode(), (RequestArgumentBinder<ServerHttpRequest<?>>) (argument, source) -> {
            if (source instanceof ServerHttpRequest<?>) {
                return convertBodyIfNecessary(bodyAnnotationBinder, argument, source, RequestType.SERVER);
            } else {
                return ArgumentBinder.BindingResult.unsatisfied();
            }
        });
        byType.put(Argument.of(PushCapableHttpRequest.class).typeHashCode(), (RequestArgumentBinder<PushCapableHttpRequest<?>>) (argument, source) -> {
            if (source instanceof PushCapableHttpRequest<?>) {
                return convertBodyIfNecessary(bodyAnnotationBinder, argument, source, RequestType.PUSH_CAPABLE);
            } else {
                return ArgumentBinder.BindingResult.unsatisfied();
            }
        });
        byType.put(Argument.of(HttpParameters.class).typeHashCode(), (RequestArgumentBinder<HttpParameters>) (argument, source) -> () -> Optional.of(source.getParameters()));
        byType.put(Argument.of(Cookies.class).typeHashCode(), (RequestArgumentBinder<Cookies>) (argument, source) -> () -> Optional.of(source.getCookies()));
        byType.put(Argument.of(Cookie.class).typeHashCode(), new CookieObjectArgumentBinder());

        defaultUnmatchedRequestArgumentBinder = new DefaultUnmatchedRequestArgumentBinder<>(
            List.of(
                new QueryValueArgumentBinder<>(conversionService),
                new RequestAttributeAnnotationBinder<>(conversionService)
            ),
            unmatchedBinders,
            List.of(bodyAnnotationBinder)
        );
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <T> void addArgumentBinder(ArgumentBinder<T, HttpRequest<?>> binder) {
        if (binder instanceof AnnotatedRequestArgumentBinder<?, ?> annotatedRequestArgumentBinder) {
            Class<? extends Annotation> annotationType = annotatedRequestArgumentBinder.getAnnotationType();
            if (binder instanceof TypedRequestArgumentBinder<?> typedRequestArgumentBinder) {
                Argument argumentType = typedRequestArgumentBinder.argumentType();
                byTypeAndAnnotation.put(new TypeAndAnnotation(argumentType, annotationType), (RequestArgumentBinder) binder);
                List<Class<?>> superTypes = typedRequestArgumentBinder.superTypes();
                if (CollectionUtils.isNotEmpty(superTypes)) {
                    for (Class<?> superType : superTypes) {
                        byTypeAndAnnotation.put(new TypeAndAnnotation(Argument.of(superType), annotationType), (RequestArgumentBinder) binder);
                    }
                }
            } else {
                byAnnotation.put(annotationType, annotatedRequestArgumentBinder);
            }

        } else if (binder instanceof TypedRequestArgumentBinder<?> typedRequestArgumentBinder) {
            byType.put(typedRequestArgumentBinder.argumentType().typeHashCode(), typedRequestArgumentBinder);
        }
    }

    @Override
    public void addUnmatchedRequestArgumentBinder(RequestArgumentBinder<Object> binder) {
        unmatchedBinders.add(binder);
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
        Optional<Class<? extends Annotation>> opt = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (opt.isPresent()) {
            Class<? extends Annotation> annotationType = opt.get();
            RequestArgumentBinder<T> binder = findBinder(argument, annotationType);
            if (binder == null) {
                binder = byAnnotation.get(annotationType);
            }
            if (binder != null) {
                return Optional.of(binder.createSpecific(argument));
            }
        } else {
            RequestArgumentBinder<T> binder = byType.get(argument.typeHashCode());
            if (binder == null) {
                binder = byType.get(Argument.of(argument.getType()).typeHashCode());
            }
            if (binder != null) {
                return Optional.of(binder.createSpecific(argument));
            }
        }
        return Optional.of(defaultUnmatchedRequestArgumentBinder.createSpecific(argument));
    }

    /**
     * @param argument       The argument
     * @param annotationType The class for annotation
     * @param <T>            The type
     * @return The request argument binder
     */
    protected <T> RequestArgumentBinder findBinder(Argument<T> argument, Class<? extends Annotation> annotationType) {
        TypeAndAnnotation key = new TypeAndAnnotation(argument, annotationType);
        return argumentBinderCache.computeIfAbsent(key, key1 -> {
            RequestArgumentBinder requestArgumentBinder = byTypeAndAnnotation.get(key1);
            if (requestArgumentBinder == null) {
                Class<?> javaType = key1.type.getType();
                for (Map.Entry<TypeAndAnnotation, RequestArgumentBinder> entry : byTypeAndAnnotation.entrySet()) {
                    TypeAndAnnotation typeAndAnnotation = entry.getKey();
                    if (typeAndAnnotation.annotation == annotationType) {

                        Argument<?> t = typeAndAnnotation.type;
                        if (t.getType().isAssignableFrom(javaType)) {
                            requestArgumentBinder = entry.getValue();
                            if (requestArgumentBinder != null) {
                                break;
                            }
                        }
                    }
                }

                if (requestArgumentBinder == null) {
                    // try the raw type
                    requestArgumentBinder = byTypeAndAnnotation.get(new TypeAndAnnotation(Argument.of(argument.getType()), annotationType));
                }
            }
            return Optional.ofNullable(requestArgumentBinder);
        }).orElse(null);

    }

    /**
     * @param byAnnotation The request argument binder
     */
    protected void registerDefaultAnnotationBinders(Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation) {
        CookieAnnotationBinder<Object> cookieAnnotationBinder = new CookieAnnotationBinder<>(conversionService);
        byAnnotation.put(cookieAnnotationBinder.getAnnotationType(), cookieAnnotationBinder);

        HeaderAnnotationBinder<Object> headerAnnotationBinder = new HeaderAnnotationBinder<>(conversionService);
        byAnnotation.put(headerAnnotationBinder.getAnnotationType(), headerAnnotationBinder);

        QueryValueArgumentBinder<Object> queryValueAnnotationBinder = new QueryValueArgumentBinder<>(conversionService);
        byAnnotation.put(queryValueAnnotationBinder.getAnnotationType(), queryValueAnnotationBinder);

        RequestAttributeAnnotationBinder<Object> requestAttributeAnnotationBinder = new RequestAttributeAnnotationBinder<>(conversionService);
        byAnnotation.put(requestAttributeAnnotationBinder.getAnnotationType(), requestAttributeAnnotationBinder);

        PathVariableAnnotationBinder<Object> pathVariableAnnotationBinder = new PathVariableAnnotationBinder<>(conversionService);
        byAnnotation.put(pathVariableAnnotationBinder.getAnnotationType(), pathVariableAnnotationBinder);

        RequestBeanAnnotationBinder<Object> requestBeanAnnotationBinder = new RequestBeanAnnotationBinder<>(this);
        byAnnotation.put(requestBeanAnnotationBinder.getAnnotationType(), requestBeanAnnotationBinder);

        PartAnnotationBinder<Object> partAnnotationBinder = new PartAnnotationBinder<>();
        byAnnotation.put(partAnnotationBinder.getAnnotationType(), partAnnotationBinder);

        if (KOTLIN_COROUTINES_SUPPORTED) {
            ContinuationArgumentBinder continuationArgumentBinder = new ContinuationArgumentBinder();
            byType.put(continuationArgumentBinder.argumentType().typeHashCode(), continuationArgumentBinder);
        }
    }

    private static ArgumentBinder.BindingResult<? extends HttpRequest<?>> convertBodyIfNecessary(
        DefaultBodyAnnotationBinder<Object> bodyAnnotationBinder,
        ArgumentConversionContext<? extends HttpRequest<?>> context,
        HttpRequest<?> source,
        RequestType type
    ) {
        if (source.getMethod().permitsRequestBody()) {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable()
                .filter(arg -> arg.getType() != Object.class)
                .filter(arg -> arg.getType() != Void.class);
            if (typeVariable.isPresent()) {
                @SuppressWarnings("unchecked")
                ArgumentConversionContext<Object> unwrappedConversionContext = ConversionContext.of((Argument<Object>) typeVariable.get());
                ArgumentBinder.BindingResult<Object> bodyBound = bodyAnnotationBinder.bindFullBody(unwrappedConversionContext, source);
                // can't use flatMap here because we return a present optional even when the body conversion failed
                return new PendingRequestBindingResult<>() {
                    @Override
                    public boolean isPending() {
                        return bodyBound instanceof PendingRequestBindingResult<Object> p && p.isPending();
                    }

                    @Override
                    public List<ConversionError> getConversionErrors() {
                        return bodyBound.getConversionErrors();
                    }

                    @Override
                    public Optional<HttpRequest<?>> getValue() {
                        Optional<Object> body = bodyBound.getValue();
                        return Optional.of(switch (type) {
                            case STANDARD -> new HttpRequestWrapper<>((HttpRequest<Object>) source) {
                                @Override
                                public Optional<Object> getBody() {
                                    return body;
                                }
                            };
                            case PUSH_CAPABLE -> new PushCapableRequestWrapper<>((HttpRequest<Object>) source, (PushCapableHttpRequest<?>) source) {
                                @Override
                                public Optional<Object> getBody() {
                                    return body;
                                }
                            };
                            case SERVER -> new ServerRequestWrapper<>((HttpRequest<Object>) source, (ServerRequestWrapper<?>) source) {
                                @Override
                                public Optional<Object> getBody() {
                                    return body;
                                }
                            };
                        });
                    }
                };
            }
        }
        return () -> Optional.of(source);
    }

    /**
     * Type and annotation.
     */
    private static final class TypeAndAnnotation {
        private final Argument<?> type;
        private final Class<? extends Annotation> annotation;

        /**
         * @param type       The type
         * @param annotation The annotation
         */
        public TypeAndAnnotation(Argument<?> type, Class<? extends Annotation> annotation) {
            this.type = type;
            this.annotation = annotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TypeAndAnnotation that = (TypeAndAnnotation) o;

            if (!type.equalsType(that.type)) {
                return false;
            }
            return annotation.equals(that.annotation);
        }

        @Override
        public int hashCode() {
            int result = type.typeHashCode();
            result = 31 * result + annotation.hashCode();
            return result;
        }
    }

    private static class PushCapableRequestWrapper<B> extends HttpRequestWrapper<B> implements PushCapableHttpRequest<B> {
        private final PushCapableHttpRequest<?> push;

        public PushCapableRequestWrapper(HttpRequest<B> primary, PushCapableHttpRequest<?> push) {
            super(primary);
            this.push = push;
        }

        @Override
        public boolean isServerPushSupported() {
            return push.isServerPushSupported();
        }

        @Override
        public PushCapableHttpRequest<B> serverPush(@NonNull HttpRequest<?> request) {
            push.serverPush(request);
            return this;
        }
    }

    private static class ServerRequestWrapper<B> extends HttpRequestWrapper<B> implements ServerHttpRequest<B> {
        private final ServerHttpRequest<?> server;

        public ServerRequestWrapper(HttpRequest<B> primary, ServerHttpRequest<?> server) {
            super(primary);
            this.server = server;
        }

        @Override
        public @NonNull ByteBody byteBody() {
            return server.byteBody();
        }

        @Override
        public @NonNull ByteBodyFactory byteBodyFactory() {
            return server.byteBodyFactory();
        }
    }

    private enum RequestType {
        STANDARD,
        PUSH_CAPABLE,
        SERVER;
    }
}
