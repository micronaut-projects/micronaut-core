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
import org.particleframework.http.MediaType;
import org.particleframework.http.annotation.Consumes;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.stereotype.Controller;
import org.particleframework.web.router.annotation.*;
import org.particleframework.web.router.annotation.Error;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Responsible for building {@link Route} instances for the annotations found in the {@link org.particleframework.web.router.annotation} package
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedMethodRouteBuilder extends DefaultRouteBuilder implements ExecutableMethodProcessor<Action> {

    private final Map<Class, BiFunction<Annotation, ExecutableMethod, Route>> httpMethodsHandlers = new LinkedHashMap<>();


    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        httpMethodsHandlers.put(Get.class, (Annotation ann, ExecutableMethod method) -> {
                    UriRoute route = GET(resolveUri(((Get) ann).value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );
        httpMethodsHandlers.put(Post.class, (Annotation ann, ExecutableMethod method) -> {
                    Post postAnn = (Post) ann;
                    MediaType[] consumes = resolveConsumes(method, postAnn.consumes());
                    Route route = POST(resolveUri(postAnn.value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (consumes.length > 0) {
                        route = route.accept(consumes);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Put.class, (Annotation ann, ExecutableMethod method) -> {
                    Put putAnn = (Put) ann;
                    MediaType[] consumes = resolveConsumes(method, putAnn.consumes());
                    Route route = PUT(resolveUri(putAnn.value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (consumes.length > 0) {
                        route = route.accept(consumes);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Patch.class, (Annotation ann, ExecutableMethod method) -> {
                    Patch patchAnn = (Patch) ann;
                    MediaType[] consumes = resolveConsumes(method, patchAnn.consumes());
                    Route route = PATCH(resolveUri(patchAnn.value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (consumes.length > 0) {
                        route = route.accept(consumes);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Delete.class, (Annotation ann, ExecutableMethod method) -> {
                    Delete deleteAnn = (Delete) ann;
                    MediaType[] consumes = resolveConsumes(method, deleteAnn.consumes());
                    Route route = DELETE(resolveUri(deleteAnn.value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (consumes.length > 0) {
                        route = route.accept(consumes);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Head.class, (Annotation ann, ExecutableMethod method) -> {
                    UriRoute route = HEAD(resolveUri(((Head) ann).value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Options.class, (Annotation ann, ExecutableMethod method) -> {
                    Options optionsAnn = (Options) ann;
                    MediaType[] consumes = resolveConsumes(method, optionsAnn.consumes());
                    Route route = OPTIONS(resolveUri(optionsAnn.value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (consumes.length > 0) {
                        route = route.accept(consumes);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Trace.class, (Annotation ann, ExecutableMethod method) -> {
                    UriRoute route = TRACE(resolveUri(((Trace) ann).value(),
                            method,
                            uriNamingStrategy),
                            method.getDeclaringType(),
                            method.getMethodName(),
                            method.getArgumentTypes());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Created Route: {}", route);
                    }
                    return route;
                }
        );

        httpMethodsHandlers.put(Error.class, (Annotation ann, ExecutableMethod method) ->
                status(((Error) ann).value(), method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes())
        );
    }

    private MediaType[] resolveConsumes(ExecutableMethod<?,?> method, String... consumes) {
        if (consumes.length > 0) {
            return Arrays.stream(consumes).map(MediaType::new).toArray(MediaType[]::new);
        } else {
            return Arrays.stream(method.findAnnotation(Consumes.class).map(Consumes::value).orElseGet(() -> {
                Controller annotation = method.getDeclaringType().getAnnotation(Controller.class);
                return annotation != null ? annotation.consumes() : new String[0];
            })).map(MediaType::new).toArray(MediaType[]::new);
        }
    }

    private String resolveUri(String value, ExecutableMethod method, UriNamingStrategy uriNamingStrategy) {
        Class declaringType = method.getDeclaringType();
        String rootUri = uriNamingStrategy.resolveUri(declaringType);
        if (value != null && value.length() > 0) {
            return rootUri + value;
        } else {
            Optional<MethodConvention> convention = MethodConvention.forMethod(method.getMethodName());
            return rootUri + convention.map(MethodConvention::uri).orElse("/" + method.getMethodName());
        }
    }

    @Override
    public void process(ExecutableMethod<?, ?> method) {
        Optional<Annotation> actionAnn = method.findAnnotationWithStereoType(Action.class);
        actionAnn.ifPresent(annotation -> {
                    Class<? extends Annotation> annotationClass = annotation.annotationType();
                    BiFunction<Annotation, ExecutableMethod, Route> handler = httpMethodsHandlers.get(annotationClass);
                    if (handler != null) {
                        handler.apply(annotation, method);
                    }
                }
        );
    }
}
