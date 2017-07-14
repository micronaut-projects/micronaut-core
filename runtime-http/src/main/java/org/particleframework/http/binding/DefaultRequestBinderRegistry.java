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
package org.particleframework.http.binding;

import org.particleframework.bind.ArgumentBinder;
import org.particleframework.bind.annotation.Bindable;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpParameters;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;
import org.particleframework.http.binding.binders.request.*;
import org.particleframework.http.cookie.Cookie;
import org.particleframework.http.cookie.Cookies;
import org.particleframework.inject.Argument;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of the {@link RequestBinderRegistry} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultRequestBinderRegistry implements RequestBinderRegistry {

    private final Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation = new LinkedHashMap<>();
    private final Map<Class, RequestArgumentBinder> byType = new LinkedHashMap<>();
    private final ConversionService<?> conversionService;

    public DefaultRequestBinderRegistry(ConversionService<?> conversionService, RequestArgumentBinder...binders) {
        this.conversionService = conversionService;


        for (RequestArgumentBinder binder : binders) {
            if(binder instanceof AnnotatedRequestArgumentBinder) {
                AnnotatedRequestArgumentBinder<?,?> annotatedRequestArgumentBinder = (AnnotatedRequestArgumentBinder) binder;
                byAnnotation.put(annotatedRequestArgumentBinder.annotationType(), annotatedRequestArgumentBinder);
            }
            else if(binder instanceof TypedRequestArgumentBinder) {
                TypedRequestArgumentBinder typedRequestArgumentBinder = (TypedRequestArgumentBinder) binder;
                byType.put(typedRequestArgumentBinder.argumentType(), typedRequestArgumentBinder);
            }
        }

        registerDefaultConverters(conversionService);
        registerDefaultAnnotationBinders(byAnnotation);

        byType.put(HttpHeaders.class, (RequestArgumentBinder<HttpHeaders>) (argument, source) -> Optional.of(source.getHeaders()));
        byType.put(HttpParameters.class, (RequestArgumentBinder<HttpParameters>) (argument, source) -> Optional.of(source.getParameters()));
        byType.put(Cookies.class, (RequestArgumentBinder<Cookies>) (argument, source) -> Optional.of(source.getCookies()));
        byType.put(Cookie.class, (RequestArgumentBinder<Cookie>)(argument, source) -> {
            Cookies cookies = source.getCookies();
            Cookie cookie = cookies.get(argument.getName());
            if(cookie == null) {
                cookie = cookies.get(NameUtils.hyphenate(argument.getName()));
            }
            return cookie != null ? Optional.of(cookie) : Optional.empty();
        });
    }

    @Override
    public <T> Optional<ArgumentBinder<T, HttpRequest>> findArgumentBinder(Argument<T> argument, HttpRequest source) {
        Annotation annotation = argument.findAnnotation(Bindable.class);
        if(annotation != null) {

            RequestArgumentBinder<T> binder = byAnnotation.get(annotation.annotationType());
            if(binder != null) {
                return Optional.of(binder);
            }
        }
        else {
            RequestArgumentBinder<T> binder = byType.get(argument.getType());
            if(binder != null) {
                return Optional.of(binder);
            }
        }
        return Optional.of(new ParameterAnnotationBinder<>(conversionService));
    }

    protected void registerDefaultConverters(ConversionService<?> conversionService) {
        conversionService.addConverter(
                CharSequence.class,
                MediaType.class,
                (TypeConverter<CharSequence, MediaType>) (object, targetType, context) -> Optional.of(new MediaType(object.toString())));

    }

    protected void registerDefaultAnnotationBinders(Map<Class<? extends Annotation>, RequestArgumentBinder> byAnnotation) {
        CookieAnnotationBinder<Object> cookieAnnotationBinder = new CookieAnnotationBinder<>(conversionService);
        byAnnotation.put(cookieAnnotationBinder.annotationType(), cookieAnnotationBinder);

        HeaderAnnotationBinder<Object> headerAnnotationBinder = new HeaderAnnotationBinder<>(conversionService);
        byAnnotation.put(headerAnnotationBinder.annotationType(), headerAnnotationBinder);

        ParameterAnnotationBinder<Object> parameterAnnotationBinder = new ParameterAnnotationBinder<>(conversionService);
        byAnnotation.put(parameterAnnotationBinder.annotationType(), parameterAnnotationBinder);
    }
}
