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
package org.particleframework.web.router;

import org.particleframework.context.ExecutionHandleLocator;
import org.particleframework.context.processor.ExecutableMethodProcessor;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.conventions.MethodConvention;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.*;
import org.particleframework.http.annotation.Error;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableMethod;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Responsible for building {@link Route} instances for the annotations found in the {@link org.particleframework.web.router.annotation} package
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedMethodRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Controller> {

    private final Map<Class, Consumer<ExecutableMethod>> httpMethodsHandlers = new LinkedHashMap<>();


    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        httpMethodsHandlers.put(Get.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = GET(resolveUri(uri,
                    method,
                    uriNamingStrategy),
                    method.getDeclaringType(),
                    method.getMethodName(),
                    method.getArgumentTypes()).produces(produces);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Post.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = POST(resolveUri(uri,
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

        httpMethodsHandlers.put(Put.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = PUT(resolveUri(uri,
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

        httpMethodsHandlers.put(Patch.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = PATCH(resolveUri(uri,
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

        httpMethodsHandlers.put(Delete.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = DELETE(resolveUri(uri,
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


        httpMethodsHandlers.put(Head.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            Route route = HEAD(resolveUri(uri,
                    method,
                    uriNamingStrategy),
                    method.getDeclaringType(),
                    method.getMethodName(),
                    method.getArgumentTypes());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Options.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            MediaType[] consumes = method.getValue(Consumes.class, MediaType[].class).orElse(null);
            MediaType[] produces = method.getValue(Produces.class, MediaType[].class).orElse(null);
            Route route = OPTIONS(resolveUri(uri,
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

        httpMethodsHandlers.put(Trace.class, (ExecutableMethod method) -> {
            String uri = method.getValue(HttpMethodMapping.class, String.class).orElse("");
            Route route = TRACE(resolveUri(uri,
                    method,
                    uriNamingStrategy),
                    method.getDeclaringType(),
                    method.getMethodName(),
                    method.getArgumentTypes());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created Route: {}", route);
            }
        });

        httpMethodsHandlers.put(Error.class, (ExecutableMethod method) -> {
            boolean isGlobal = method.getValue(Error.class, "global", boolean.class).orElse(false);
            Class declaringType = method.getDeclaringType();
            if (method.isPresent(Error.class, "status")) {
                        Optional<HttpStatus> value = method.getValue(Error.class, "status", HttpStatus.class);
                        value.ifPresent(httpStatus -> status(httpStatus, declaringType, method.getMethodName(), method.getArgumentTypes()));
                    } else if (method.isPresent(Error.class, "value")) {
                        Optional<Class> aClass = method.classValue(Error.class);
                        aClass.ifPresent(exceptionType ->
                                {
                                    if (Throwable.class.isAssignableFrom(exceptionType)) {
                                        if(isGlobal) {
                                            //noinspection unchecked
                                            error(exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
                                        }
                                        else {
                                            error(declaringType, exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
                                        }
                                    }
                                }
                        );
                    } else {
                        if(isGlobal) {
                            error(Throwable.class, declaringType, method.getMethodName(), method.getArgumentTypes());
                        }
                        else {
                            error(declaringType, Throwable.class, declaringType, method.getMethodName(), method.getArgumentTypes());
                        }
                    }

                }

        );
    }

    private String resolveUri(String value, ExecutableMethod method, UriNamingStrategy uriNamingStrategy) {
        Class declaringType = method.getDeclaringType();
        String rootUri = uriNamingStrategy.resolveUri(declaringType);
        if (StringUtils.isNotEmpty(value)) {
            if (value.length() == 1 && value.charAt(0) == '/') {
                return rootUri;
            } else {
                return rootUri + value;
            }
        } else {
            return rootUri + uriNamingStrategy.resolveUri(method.getMethodName());

        }
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Optional<Class<? extends Annotation>> actionAnn = method.getAnnotationTypeByStereotype(HttpMethodMapping.class);
        actionAnn.ifPresent(annotationClass -> {
                    Consumer<ExecutableMethod> handler = httpMethodsHandlers.get(annotationClass);
                    if (handler != null) {
                        handler.accept(method);
                    }
                }
        );
    }
}
