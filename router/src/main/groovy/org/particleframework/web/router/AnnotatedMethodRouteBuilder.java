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
import org.particleframework.http.MediaType;
import org.particleframework.inject.ExecutableMethod;
import org.particleframework.stereotype.Controller;
import org.particleframework.web.router.annotation.*;
import org.particleframework.web.router.annotation.Error;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

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
        httpMethodsHandlers.put(Get.class, (Annotation ann, ExecutableMethod method) ->
                GET(resolveUri(((Get) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );
        httpMethodsHandlers.put(Post.class, (Annotation ann, ExecutableMethod method) ->
                POST(resolveUri(((Post) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Put.class, (Annotation ann, ExecutableMethod method) ->
                PUT(resolveUri(((Put) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Patch.class, (Annotation ann, ExecutableMethod method) ->
                PATCH(resolveUri(((Patch) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Delete.class, (Annotation ann, ExecutableMethod method) ->
                DELETE(resolveUri(((Delete) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Head.class, (Annotation ann, ExecutableMethod method) ->
                HEAD(resolveUri(((Head) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Options.class, (Annotation ann, ExecutableMethod method) ->
                OPTIONS(resolveUri(((Options) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Trace.class, (Annotation ann, ExecutableMethod method) ->
                TRACE(resolveUri(((Trace) ann).value(),
                        method,
                        uriNamingStrategy),
                        method.getDeclaringType(),
                        method.getMethodName(),
                        method.getArgumentTypes())
        );

        httpMethodsHandlers.put(Error.class, (Annotation ann, ExecutableMethod method) ->
                status(((Error)ann).value(), method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes())
        );
    }

    private String resolveUri(String value, ExecutableMethod method, UriNamingStrategy uriNamingStrategy) {
        Class declaringType = method.getDeclaringType();
        String rootUri = uriNamingStrategy.resolveUri(declaringType);
        if (value != null && value.length() > 0) {
            return rootUri + value;
        } else {
            return rootUri + "/" + method.getMethodName();
        }
    }

    @Override
    public void process(ExecutableMethod method) {
        Annotation annotation = method.findAnnotation(Action.class);
         if (annotation != null) {
            Class<? extends Annotation> annotationClass = annotation.annotationType();
            BiFunction<Annotation, ExecutableMethod, Route> handler = httpMethodsHandlers.get(annotationClass);
            if (handler != null) {
                Route route = handler.apply(annotation, method);
                Class<?> declaringType = method.getDeclaringType();
                Controller controllerAnn = declaringType.getAnnotation(Controller.class);
                if (controllerAnn != null) {
                    String[] consumes = controllerAnn.consumes();
                    route =processConsumes(route, consumes);
                }
                Consumes consumesAnn = declaringType.getAnnotation(Consumes.class);
                if (consumesAnn != null) {
                    String[] consumes = consumesAnn.value();
                    route =processConsumes(route, consumes);
                }
            }
        }
    }

    protected Route processConsumes(Route route, String[] consumes) {
        MediaType[] accepts = Arrays.stream(consumes).map(MediaType::new).toArray(MediaType[]::new);
        return route.accept(accepts);
    }
}
