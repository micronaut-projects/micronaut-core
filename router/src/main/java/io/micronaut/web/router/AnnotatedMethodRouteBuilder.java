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
package io.micronaut.web.router;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassLoadingReporter;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.*;
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

    private static final MediaType[] DEFAULT_MEDIA_TYPES = {MediaType.APPLICATION_JSON_TYPE};
    private final Map<Class, BiConsumer<BeanDefinition, ExecutableMethod>> httpMethodsHandlers = new LinkedHashMap<>();

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        httpMethodsHandlers.put(Get.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Get> annotation = method.getAnnotation(Get.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] produces = resolveProduces(method);
                Route route = GET(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
                if (method.booleanValue(Get.class, "headRoute").orElse(true)) {
                    route = HEAD(resolveUri(bean, uri,
                            method,
                            uriNamingStrategy),
                            bean,
                            method).produces(produces);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                }
            }
        });

        httpMethodsHandlers.put(Post.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Post> annotation = method.getAnnotation(Post.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                Route route = POST(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(CustomHttpMethod.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<CustomHttpMethod> annotation = method.getAnnotation(CustomHttpMethod.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                String methodName = method.stringValue(CustomHttpMethod.class, "method").get();
                Route route = buildBeanRoute(methodName, HttpMethod.CUSTOM, resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Put.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Put> annotation = method.getAnnotation(Put.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                Route route = PUT(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Patch.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Patch> annotation = method.getAnnotation(Patch.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                Route route = PATCH(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Delete.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Delete> annotation = method.getAnnotation(Delete.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                Route route = DELETE(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });


        httpMethodsHandlers.put(Head.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Head> annotation = method.getAnnotation(Head.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                Route route = HEAD(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Options.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Options> annotation = method.getAnnotation(Options.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] consumes = resolveConsumes(method);
                MediaType[] produces = resolveProduces(method);
                Route route = OPTIONS(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                route = route.consumes(consumes).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Trace.class, (BeanDefinition bean, ExecutableMethod method) -> {
            AnnotationValue<Trace> annotation = method.getAnnotation(Trace.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(HttpMethodMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                Route route = TRACE(resolveUri(bean, uri,
                        method,
                        uriNamingStrategy),
                        bean,
                        method);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        });

        httpMethodsHandlers.put(Error.class, (BeanDefinition bean, ExecutableMethod method) -> {
                boolean isGlobal = method.isTrue(Error.class, "global");
                Class declaringType = bean.getBeanType();
                if (method.isPresent(Error.class, "status")) {
                    Optional<HttpStatus> value = method.enumValue(Error.class, "status", HttpStatus.class);
                    value.ifPresent(httpStatus -> {
                        if (isGlobal) {
                            status(httpStatus, declaringType, method.getMethodName(), method.getArgumentTypes());
                        } else {
                            status(declaringType, httpStatus, declaringType, method.getMethodName(), method.getArgumentTypes());
                        }
                    });
                } else {
                    Class exceptionType = null;
                    if (method.isPresent(Error.class, AnnotationMetadata.VALUE_MEMBER)) {
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

    private MediaType[] resolveConsumes(ExecutableMethod method) {
        MediaType[] consumes = MediaType.of(method.stringValues(Consumes.class));
        if (ArrayUtils.isEmpty(consumes)) {
            consumes = DEFAULT_MEDIA_TYPES;
        }
        return consumes;
    }

    private MediaType[] resolveProduces(ExecutableMethod method) {
        MediaType[] produces = MediaType.of(method.stringValues(Produces.class));
        if (ArrayUtils.isEmpty(produces)) {
            produces = DEFAULT_MEDIA_TYPES;
        }
        return produces;
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

        if (!actionAnn.isPresent() && method.isDeclaredAnnotationPresent(UriMapping.class)) {
            AnnotationValue<UriMapping> annotation = method.getAnnotation(UriMapping.class);
            Set<String> uris = CollectionUtils.setOf(annotation.stringValues("uris"));
            uris.add(method.stringValue(UriMapping.class).orElse(UriMapping.DEFAULT_URI));
            for (String uri: uris) {
                MediaType[] produces = MediaType.of(method.stringValues(Produces.class));
                Route route = GET(resolveUri(beanDefinition, uri,
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes()).produces(produces);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Created Route: {}", route);
                }
            }
        }

    }

    private String resolveUri(BeanDefinition bean, String value, ExecutableMethod method, UriNamingStrategy uriNamingStrategy) {
        UriTemplate rootUri = UriTemplate.of(uriNamingStrategy.resolveUri(bean));
        if (StringUtils.isNotEmpty(value)) {
            return rootUri.nest(value).toString();
        } else {
            return rootUri.nest(uriNamingStrategy.resolveUri(method.getMethodName())).toString();
        }
    }
}
