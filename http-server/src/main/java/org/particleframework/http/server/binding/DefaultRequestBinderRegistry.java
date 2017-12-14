/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.binding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.bind.ArgumentBinder;
import org.particleframework.core.bind.annotation.Bindable;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpParameters;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Body;
import org.particleframework.http.server.binding.binders.*;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.core.type.Argument;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Default implementation of the {@link RequestBinderRegistry} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRequestBinderRegistry implements RequestBinderRegistry {

    private final Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation = new LinkedHashMap<>();
    private final Map<TypeAndAnnotation, RequestArgumentBinder> byTypeAndAnnotation = new LinkedHashMap<>();
    private final Map<Argument<?>, RequestArgumentBinder> byType = new LinkedHashMap<>();
    private final ConversionService<?> conversionService;
    private final Cache<TypeAndAnnotation, Optional<RequestArgumentBinder>> argumentBinderCache = Caffeine.newBuilder().maximumSize(30).build();

    public DefaultRequestBinderRegistry(ConversionService conversionService, RequestArgumentBinder...binders) {
        this.conversionService = conversionService;


        for (RequestArgumentBinder binder : binders) {
            if(binder instanceof AnnotatedRequestArgumentBinder) {
                AnnotatedRequestArgumentBinder<?,?> annotatedRequestArgumentBinder = (AnnotatedRequestArgumentBinder) binder;
                Class<? extends Annotation> annotationType = annotatedRequestArgumentBinder.getAnnotationType();
                if(binder instanceof TypedRequestArgumentBinder) {
                    TypedRequestArgumentBinder typedRequestArgumentBinder = (TypedRequestArgumentBinder) binder;
                    Argument argumentType = typedRequestArgumentBinder.argumentType();
                    byTypeAndAnnotation.put(new TypeAndAnnotation(argumentType, annotationType), binder);
                    Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(argumentType.getType());
                    for (Class<?> itfce : allInterfaces) {
                        byTypeAndAnnotation.put(new TypeAndAnnotation(Argument.of(itfce), annotationType), binder);
                    }
                }
                else {
                    byAnnotation.put(annotationType, annotatedRequestArgumentBinder);
                }

            }
            else if(binder instanceof TypedRequestArgumentBinder) {
                TypedRequestArgumentBinder typedRequestArgumentBinder = (TypedRequestArgumentBinder) binder;
                byType.put(typedRequestArgumentBinder.argumentType(), typedRequestArgumentBinder);
            }
        }

        registerDefaultConverters(conversionService);
        registerDefaultAnnotationBinders(byAnnotation);

        byType.put(Argument.of(HttpHeaders.class), (RequestArgumentBinder<HttpHeaders>) (argument, source) -> () -> Optional.of(source.getHeaders()));
        byType.put(Argument.of(HttpRequest.class), (RequestArgumentBinder<HttpRequest>) (argument, source) -> () -> Optional.of(source));
        byType.put(Argument.of(HttpParameters.class), (RequestArgumentBinder<HttpParameters>) (argument, source) -> () -> Optional.of(source.getParameters()));
        byType.put(Argument.of(Cookies.class), (RequestArgumentBinder<Cookies>) (argument, source) -> () -> Optional.of(source.getCookies()));
        byType.put(Argument.of(Cookie.class), (RequestArgumentBinder<Cookie>)(context, source) -> {
            Cookies cookies = source.getCookies();
            String name = context.getArgument().getName();
            Cookie cookie = cookies.get(name);
            if(cookie == null) {
                cookie = cookies.get(NameUtils.hyphenate(name));
            }
            Cookie finalCookie = cookie;
            return () -> finalCookie != null ? Optional.of(finalCookie) : Optional.empty();
        });
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument, HttpRequest<?> source) {
        Optional<Annotation> annotation = argument.findAnnotationWithStereoType(Bindable.class);
        if(annotation.isPresent()) {
            Class<? extends Annotation> annotationType = annotation.get().annotationType();
            RequestArgumentBinder<T> binder = findBinder(argument, annotationType);
            if(binder ==  null) {
                binder = byAnnotation.get(annotationType);
            }
            if(binder != null) {
                return Optional.of(binder);
            }
        }
        else {
            RequestArgumentBinder<T> binder = byType.get(argument);
            if(binder != null) {
                return Optional.of(binder);
            }
            else {
                binder = byType.get(Argument.of(argument.getType()));
                if(binder != null) {
                    return Optional.of(binder);
                }
            }
        }
        return Optional.of(new ParameterAnnotationBinder<>(conversionService));
    }

    protected <T> RequestArgumentBinder findBinder(Argument<T> argument, Class<? extends Annotation> annotationType) {
        TypeAndAnnotation key = new TypeAndAnnotation(argument, annotationType);
        return argumentBinderCache.get(key, key1 -> {
            RequestArgumentBinder requestArgumentBinder = byTypeAndAnnotation.get(key1);
            if(requestArgumentBinder == null) {
                Class<?> javaType = key1.type.getType();
                Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(javaType);
                for (Class itfce : allInterfaces) {
                    requestArgumentBinder = byTypeAndAnnotation.get(new TypeAndAnnotation(Argument.of(itfce), annotationType));
                    if(requestArgumentBinder != null) break;
                }
            }
            return Optional.ofNullable(requestArgumentBinder);
        }).orElse(null);

    }

    protected void registerDefaultConverters(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                MediaType.class,(object, targetType, context) -> Optional.of(new MediaType(object.toString())));

    }

    protected void registerDefaultAnnotationBinders(Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation) {
        DefaultBodyAnnotationBinder bodyBinder = new DefaultBodyAnnotationBinder(conversionService);
        byAnnotation.put(Body.class, bodyBinder);

        CookieAnnotationBinder<Object> cookieAnnotationBinder = new CookieAnnotationBinder<>(conversionService);
        byAnnotation.put(cookieAnnotationBinder.getAnnotationType(), cookieAnnotationBinder);

        HeaderAnnotationBinder<Object> headerAnnotationBinder = new HeaderAnnotationBinder<>(conversionService);
        byAnnotation.put(headerAnnotationBinder.getAnnotationType(), headerAnnotationBinder);

        ParameterAnnotationBinder<Object> parameterAnnotationBinder = new ParameterAnnotationBinder<>(conversionService);
        byAnnotation.put(parameterAnnotationBinder.getAnnotationType(), parameterAnnotationBinder);
    }

    private static final class TypeAndAnnotation {
        private final Argument<?> type;
        private final Class<? extends Annotation> annotation;

        public TypeAndAnnotation(Argument<?> type, Class<? extends Annotation> annotation) {
            this.type = type;
            this.annotation = annotation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            TypeAndAnnotation that = (TypeAndAnnotation) o;

            if (!type.equals(that.type)) return false;
            return annotation.equals(that.annotation);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + annotation.hashCode();
            return result;
        }
    }
}
