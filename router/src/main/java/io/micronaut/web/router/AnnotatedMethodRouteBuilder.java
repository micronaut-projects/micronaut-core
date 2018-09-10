/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.web.router;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Trace;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Responsible for building {@link Route} instances for the annotations found in the {@code io.micronaut.http.annotation}
 * package.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedMethodRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Controller> {

    private final Map<Class, BiConsumer<BeanDefinition, ExecutableMethod>> httpMethodsHandlers = new LinkedHashMap<>();

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        httpMethodsHandlers.put(Get.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = GET(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes()).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Post.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = POST(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            route = route.consumes(consumes).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Put.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = PUT(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            route = route.consumes(consumes).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Patch.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = PATCH(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            route = route.consumes(consumes).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Delete.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = DELETE(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            route = route.consumes(consumes).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });


        httpMethodsHandlers.put(Head.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            Route route = HEAD(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Options.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = OPTIONS(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            route = route.consumes(consumes).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Trace.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<HttpMethodMapping> mapping = method.getAnnotation(HttpMethodMapping.class);
            String uri = mapping.getRequiredValue(String.class);
            Route route = TRACE(resolveUri(bean, uri,
                method,
                uriNamingStrategy),
                method.getDeclaringType(),
                method.getMethodName(),
                method.getArgumentTypes());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Error.class, (BeanDefinition bean, ExecutableMethod method) -> {
                boolean isGlobal = method.getValue(Error.class, "global", boolean.class).orElse(false);
                Class declaringType = method.getDeclaringType();
                if (method.isPresent(Error.class, "status")) {
                    Optional<HttpStatus> value = method.getValue(Error.class, "status", HttpStatus.class);
                    value.ifPresent(httpStatus -> {
                        if (isGlobal) {
                            status(httpStatus, declaringType, method.getMethodName(), method.getArgumentTypes());
                        } else {
                            status(declaringType, httpStatus, declaringType, method.getMethodName(), method.getArgumentTypes());
                        }
                    });
                } else {
                    Class exceptionType = null;
                    if (method.isPresent(Error.class, "value")) {
                        Optional<Class> annotationValue = method.classValue(Error.class);
                        if (annotationValue.isPresent()) {
                            if (Throwable.class.isAssignableFrom(annotationValue.get())) {
                                exceptionType = annotationValue.get();
                            }
                        }
                    }
                    if (exceptionType == null) {
                        exceptionType = Arrays.stream(method.getArgumentTypes())
                                .filter(Throwable.class::isAssignableFrom)
                                .findFirst()
                                .orElse(Throwable.class);
                    }

                    if (isGlobal) {
                        //noinspection unchecked
                        error(exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
                    } else {
                        //noinspection unchecked
                        error(declaringType, exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
                    }
                }
            }
        );
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Optional<Class<? extends Annotation>> actionAnn = method.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        actionAnn.ifPresent(annotationClass -> {
                BiConsumer<BeanDefinition, ExecutableMethod> handler = httpMethodsHandlers.get(annotationClass);
                if (handler != null) {
                    ClassLoadingReporter.reportBeanPresent(method.getReturnType().getType());
                    for (Class argumentType : method.getArgumentTypes()) {
                        ClassLoadingReporter.reportBeanPresent(argumentType);
                    }
                    handler.accept(beanDefinition, method);
                }
            }
        );
    }

    private String resolveUri(BeanDefinition bean, String value, ExecutableMethod method, UriNamingStrategy uriNamingStrategy) {
        UriTemplate rootUri = UriTemplate.of(uriNamingStrategy.resolveUri(bean));
        if (StringUtils.isNotEmpty(value)) {
            boolean isFirstCharSlash = value.charAt(0) == '/';
            boolean isFirstCharVar = value.charAt(0) == '{';
            if (value.length() == 1 && isFirstCharSlash) {
                return rootUri.toString();
            } else {
                return rootUri.nest(isFirstCharSlash || isFirstCharVar ? value : '/' + value).toString();
            }
        } else {
            return rootUri.nest(uriNamingStrategy.resolveUri(method.getMethodName())).toString();
        }
    }
}
