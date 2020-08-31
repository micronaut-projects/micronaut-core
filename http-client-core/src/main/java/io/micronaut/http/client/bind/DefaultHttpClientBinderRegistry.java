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
package io.micronaut.http.client.bind;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.RequestAttribute;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Default implementation of {@link HttpClientBinderRegistry} that searches by
 * annotation then by type.
 *
 * @author James Kleeh
 * @since 2.1.0
 */
@Singleton
@Internal
public class DefaultHttpClientBinderRegistry implements HttpClientBinderRegistry {

    private final Map<Class<? extends Annotation>, ClientArgumentRequestBinder> byAnnotation = new LinkedHashMap<>();
    private final Map<Integer, ClientArgumentRequestBinder> byType = new LinkedHashMap<>();

    /**
     * @param conversionService The conversion service
     * @param binders           The request argument binders
     */
    @Inject
    public DefaultHttpClientBinderRegistry(ConversionService<?> conversionService,
                                           List<ClientArgumentRequestBinder> binders) {
        byType.put(Argument.of(HttpHeaders.class).typeHashCode(), (ClientArgumentRequestBinder<HttpHeaders>) (context, value, request) -> {
            value.forEachValue(request::header);
        });
        byType.put(Argument.of(Cookies.class).typeHashCode(), (ClientArgumentRequestBinder<Cookies>) (context, value, request) -> {
            request.cookies(value.getAll());
        });
        byType.put(Argument.of(Cookie.class).typeHashCode(), (ClientArgumentRequestBinder<Cookie>) (context, value, request) -> {
            request.cookie(value);
        });
        byType.put(Argument.of(BasicAuth.class).typeHashCode(), (ClientArgumentRequestBinder<BasicAuth>) (context, value, request) -> {
            request.basicAuth(value.getUsername(), value.getPassword());
        });
        byAnnotation.put(CookieValue.class, (context, value, request) -> {
            String cookieName = context.getAnnotationMetadata().stringValue(CookieValue.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(context.getArgument().getName());

            conversionService.convert(value, String.class)
                    .ifPresent(o -> request.cookie(Cookie.of(cookieName, o)));
        });
        byAnnotation.put(Header.class, (context, value, request) -> {
            AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
            String headerName = annotationMetadata
                    .stringValue(Header.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(NameUtils.hyphenate(context.getArgument().getName()));

            conversionService.convert(value, String.class)
                    .ifPresent(header -> request.getHeaders().set(headerName, header));
        });
        byAnnotation.put(RequestAttribute.class, (context, value, request) -> {
            AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
            String attributeName = annotationMetadata
                    .stringValue(RequestAttribute.class)
                    .filter(StringUtils::isNotEmpty)
                    .orElse(NameUtils.hyphenate(context.getArgument().getName()));
            request.getAttributes().put(attributeName, value);
        });
        byAnnotation.put(Body.class, (ClientArgumentRequestBinder<Object>) (context, value, request) -> {
            request.body(value);
        });

        if (CollectionUtils.isNotEmpty(binders)) {
            for (ClientArgumentRequestBinder binder : binders) {
                addBinder(binder);
            }
        }
    }

    @Override
    public <T> Optional<ClientArgumentRequestBinder<T>> findArgumentBinder(Argument<T> argument) {
        Optional<Class<? extends Annotation>> opt = argument.getAnnotationMetadata().getAnnotationTypeByStereotype(Bindable.class);
        if (opt.isPresent()) {
            Class<? extends Annotation> annotationType = opt.get();
            ClientArgumentRequestBinder<T> binder = byAnnotation.get(annotationType);
            return Optional.ofNullable(binder);
        } else {
            ClientArgumentRequestBinder<T> binder = byType.get(argument.typeHashCode());
            if (binder != null) {
                return Optional.of(binder);
            } else {
                binder = byType.get(Argument.of(argument.getType()).typeHashCode());
                return Optional.ofNullable(binder);
            }
        }
    }

    /**
     * Adds a binder to the registry.
     *
     * @param binder The binder
     * @param <T> The type
     */
    public <T> void addBinder(ClientArgumentRequestBinder<T> binder) {
        if (binder instanceof AnnotatedClientArgumentRequestBinder) {
            AnnotatedClientArgumentRequestBinder<?, ?> annotatedRequestArgumentBinder = (AnnotatedClientArgumentRequestBinder) binder;
            Class<? extends Annotation> annotationType = annotatedRequestArgumentBinder.getAnnotationType();
            byAnnotation.put(annotationType, annotatedRequestArgumentBinder);
        } else if (binder instanceof TypedClientArgumentRequestBinder) {
            TypedClientArgumentRequestBinder<?> typedRequestArgumentBinder = (TypedClientArgumentRequestBinder) binder;
            byType.put(typedRequestArgumentBinder.argumentType().typeHashCode(), typedRequestArgumentBinder);
            List<Class<?>> superTypes = typedRequestArgumentBinder.superTypes();
            if (CollectionUtils.isNotEmpty(superTypes)) {
                for (Class<?> superType : superTypes) {
                    byType.put(Argument.of(superType).typeHashCode(), typedRequestArgumentBinder);
                }
            } else if (typedRequestArgumentBinder.supportsSuperTypes()) {
                Set<Class> allInterfaces = ReflectionUtils.getAllInterfaces(typedRequestArgumentBinder.argumentType().getType());
                if (ClassUtils.REFLECTION_LOGGER.isWarnEnabled()) {
                    ClassUtils.REFLECTION_LOGGER.warn(
                            "Request argument binder [{}] triggered the use of reflection for types {}",
                            typedRequestArgumentBinder,
                            allInterfaces
                    );
                }

                for (Class<?> itfce : allInterfaces) {
                    byType.put(Argument.of(itfce).typeHashCode(), typedRequestArgumentBinder);
                }
            }
        }
    }
}
