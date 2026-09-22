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
package io.micronaut.web.router;

import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Optional;

/**
 * Responsible for building {@link Route} instances for the annotations found in the {@code io.micronaut.http.annotation}
 * package.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class AnnotatedMethodRouteBuilder extends DefaultRouteBuilder implements BeanDefinitionProcessor<Controller> {

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext beanContext) {
        RouteDefinitions.UriResolver uriResolver = new RouteDefinitions.UriResolver() {
            @Override
            public String controllerUri() {
                return uriNamingStrategy.resolveUri(beanDefinition);
            }

            @Override
            public String methodUri(String methodName) {
                return uriNamingStrategy.resolveUri(methodName);
            }
        };
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (method.getAnnotationTypeByStereotype(HttpMethodMapping.class).orElse(null) == Error.class) {
                processError(beanDefinition, method);
                continue;
            }
            for (RouteDefinitions.RouteSpec spec : RouteDefinitions.resolve(beanDefinition, method, method.getMethodName(), uriResolver)) {
                addRoute(beanDefinition, method, spec);
            }
        }
    }

    /**
     * Add the route of a route definition.
     *
     * @param beanDefinition The controller
     * @param method         The controller method
     * @param spec           The route definition
     */
    void addRoute(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method, RouteDefinitions.RouteSpec spec) {
        UriRoute route;
        if (spec.declaringTypeTarget()) {
            route = GET(spec.uri(), method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes());
        } else {
            route = buildBeanRoute(spec.httpMethodName(), spec.httpMethod(), spec.uri(), beanDefinition, method);
        }
        if (spec.consumes() != null) {
            route = route.consumes(spec.consumes());
        }
        if (spec.produces() != null) {
            route = route.produces(spec.produces());
        }
        if (spec.implicitHead() && route instanceof DefaultUriRoute defaultUriRoute) {
            // Flag the route as implicit so that, should it ever compete with a user-declared
            // @Head route for the same request, route resolution can prefer the explicit one
            // instead of failing with a DuplicateRouteException. See UriRouteInfo#isImplicitHead().
            defaultUriRoute.markImplicitHead();
        }
        if (spec.port() > -1) {
            route.exposedPort(spec.port());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created Route: {}", route);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void processError(BeanDefinition<?> bean, ExecutableMethod method) {
        boolean isGlobal = method.isTrue(Error.class, "global");
        Class<?> declaringType = bean.getBeanType();
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
            Class<? extends Throwable> exceptionType = null;
            if (method.isPresent(Error.class, AnnotationMetadata.VALUE_MEMBER)) {
                Optional<Class> annotationValue = method.classValue(Error.class);
                if (annotationValue.isPresent() && Throwable.class.isAssignableFrom(annotationValue.get())) {
                    exceptionType = (Class<? extends Throwable>) annotationValue.get();
                }
            }
            if (exceptionType == null) {
                exceptionType = Arrays.stream(method.getArgumentTypes())
                        .filter(Throwable.class::isAssignableFrom)
                        .findFirst()
                        .orElse(Throwable.class);
            }

            if (isGlobal) {
                error(exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
            } else {
                error(declaringType, exceptionType, declaringType, method.getMethodName(), method.getArgumentTypes());
            }
        }
    }
}
