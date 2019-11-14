/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.bind;

import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.bind.binders.*;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;

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
    private final ConversionService<?> conversionService;
    private final Map<TypeAndAnnotation, Optional<RequestArgumentBinder>> argumentBinderCache =
        new ConcurrentLinkedHashMap.Builder<TypeAndAnnotation, Optional<RequestArgumentBinder>>().maximumWeightedCapacity(CACHE_MAX_SIZE).build();

    /**
     * @param conversionService The conversion service
     * @param binders           The request argument binders
     */
    public DefaultRequestBinderRegistry(ConversionService conversionService, RequestArgumentBinder... binders) {
        this(conversionService, Arrays.asList(binders));
    }

    /**
     * @param conversionService The conversion service
     * @param binders           The request argument binders
     */
    @Inject public DefaultRequestBinderRegistry(ConversionService conversionService, List<RequestArgumentBinder> binders) {
        this.conversionService = conversionService;

        if (CollectionUtils.isNotEmpty(binders)) {
            for (RequestArgumentBinder binder : binders) {
                if (binder instanceof AnnotatedRequestArgumentBinder) {
                    AnnotatedRequestArgumentBinder<?, ?> annotatedRequestArgumentBinder = (AnnotatedRequestArgumentBinder) binder;
                    Class<? extends Annotation> annotationType = annotatedRequestArgumentBinder.getAnnotationType();
                    if (binder instanceof TypedRequestArgumentBinder) {
                        TypedRequestArgumentBinder typedRequestArgumentBinder = (TypedRequestArgumentBinder) binder;
                        Argument argumentType = typedRequestArgumentBinder.argumentType();
                        byTypeAndAnnotation.put(new TypeAndAnnotation(argumentType, annotationType), binder);
                        if (((TypedRequestArgumentBinder) binder).supportsSuperTypes()) {
                            Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(argumentType.getType());
                            for (Class<?> itfce : allInterfaces) {
                                byTypeAndAnnotation.put(new TypeAndAnnotation(Argument.of(itfce), annotationType), binder);
                            }
                        }
                    } else {
                        byAnnotation.put(annotationType, annotatedRequestArgumentBinder);
                    }

                } else if (binder instanceof TypedRequestArgumentBinder) {
                    TypedRequestArgumentBinder typedRequestArgumentBinder = (TypedRequestArgumentBinder) binder;
                    byType.put(typedRequestArgumentBinder.argumentType().typeHashCode(), typedRequestArgumentBinder);
                }
            }
        }

        registerDefaultConverters(conversionService);
        registerDefaultAnnotationBinders(byAnnotation);

        byType.put(Argument.of(HttpHeaders.class).typeHashCode(), (RequestArgumentBinder<HttpHeaders>) (argument, source) -> () -> Optional.of(source.getHeaders()));
        byType.put(Argument.of(HttpRequest.class).typeHashCode(), (RequestArgumentBinder<HttpRequest>) (argument, source) -> {
            Optional<Argument<?>> typeVariable = argument.getFirstTypeVariable().filter(arg -> arg.getType() != Object.class);
            if (typeVariable.isPresent() && HttpMethod.permitsRequestBody(source.getMethod())) {
                if (source.getBody().isPresent()) {
                    return () -> Optional.of(new FullHttpRequest(source, typeVariable.get()));
                } else {
                    return ArgumentBinder.BindingResult.UNSATISFIED;
                }
            } else {
                return () -> Optional.of(source);
            }
        });
        byType.put(Argument.of(HttpParameters.class).typeHashCode(), (RequestArgumentBinder<HttpParameters>) (argument, source) -> () -> Optional.of(source.getParameters()));
        byType.put(Argument.of(Cookies.class).typeHashCode(), (RequestArgumentBinder<Cookies>) (argument, source) -> () -> Optional.of(source.getCookies()));
        byType.put(Argument.of(Cookie.class).typeHashCode(), (RequestArgumentBinder<Cookie>) (context, source) -> {
            Cookies cookies = source.getCookies();
            String name = context.getArgument().getName();
            Cookie cookie = cookies.get(name);
            if (cookie == null) {
                cookie = cookies.get(NameUtils.hyphenate(name));
            }
            Cookie finalCookie = cookie;
            return () -> finalCookie != null ? Optional.of(finalCookie) : Optional.empty();
        });
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument, HttpRequest<?> source) {
        Optional<Class<? extends Annotation>> opt = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (opt.isPresent()) {
            Class<? extends Annotation> annotationType = opt.get();
            RequestArgumentBinder<T> binder = findBinder(argument, annotationType);
            if (binder == null) {
                binder = byAnnotation.get(annotationType);
            }
            if (binder != null) {
                return Optional.of(binder);
            }
        } else {
            RequestArgumentBinder<T> binder = byType.get(argument.typeHashCode());
            if (binder != null) {
                return Optional.of(binder);
            } else {
                binder = byType.get(Argument.of(argument.getType()).typeHashCode());
                if (binder != null) {
                    return Optional.of(binder);
                }
            }
        }
        return Optional.of(new ParameterAnnotationBinder<>(conversionService));
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
                Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(javaType);
                for (Class itfce : allInterfaces) {
                    requestArgumentBinder = byTypeAndAnnotation.get(new TypeAndAnnotation(Argument.of(itfce), annotationType));
                    if (requestArgumentBinder != null) {
                        break;
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
     * Registers a default converter.
     *
     * @param conversionService The conversion service
     */
    protected void registerDefaultConverters(ConversionService<?> conversionService) {
        conversionService.addConverter(
            CharSequence.class,
            MediaType.class, (object, targetType, context) -> {
                    if (StringUtils.isEmpty(object)) {
                        return Optional.empty();
                    } else {
                        final String str = object.toString();
                        try {
                            return Optional.of(new MediaType(str));
                        } catch (IllegalArgumentException e) {
                            context.reject(e);
                            return Optional.empty();
                        }
                    }
                });

    }

    /**
     * @param byAnnotation The request argument binder
     */
    protected void registerDefaultAnnotationBinders(Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation) {
        DefaultBodyAnnotationBinder bodyBinder = new DefaultBodyAnnotationBinder(conversionService);
        byAnnotation.put(Body.class, bodyBinder);

        CookieAnnotationBinder<Object> cookieAnnotationBinder = new CookieAnnotationBinder<>(conversionService);
        byAnnotation.put(cookieAnnotationBinder.getAnnotationType(), cookieAnnotationBinder);

        HeaderAnnotationBinder<Object> headerAnnotationBinder = new HeaderAnnotationBinder<>(conversionService);
        byAnnotation.put(headerAnnotationBinder.getAnnotationType(), headerAnnotationBinder);

        ParameterAnnotationBinder<Object> parameterAnnotationBinder = new ParameterAnnotationBinder<>(conversionService);
        byAnnotation.put(parameterAnnotationBinder.getAnnotationType(), parameterAnnotationBinder);

        RequestAttributeAnnotationBinder<Object> requestAttributeAnnotationBinder = new RequestAttributeAnnotationBinder<>(conversionService);
        byAnnotation.put(requestAttributeAnnotationBinder.getAnnotationType(), requestAttributeAnnotationBinder);

        PathVariableAnnotationBinder<Object> pathVariableAnnotationBinder = new PathVariableAnnotationBinder<>(conversionService);
        byAnnotation.put(pathVariableAnnotationBinder.getAnnotationType(), pathVariableAnnotationBinder);

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
}
