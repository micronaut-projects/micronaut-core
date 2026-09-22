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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * The precompiled routes by controller type name.
     */
    private final Map<String, List<PrecompiledRoute>> precompiledRoutes;
    /**
     * The enabled controllers whose routes are precompiled, in processing order.
     */
    private final Map<String, PrecompiledController> precompiledControllers = new LinkedHashMap<>();

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        // precompiled routes are derived by this class with the default naming strategy and no context path
        if (getClass() == AnnotatedMethodRouteBuilder.class
            && uriNamingStrategy.getClass() == HyphenatedUriNamingStrategy.class
            && "/".equals(uriNamingStrategy.resolveUri(""))) {
            ClassLoader classLoader = executionHandleLocator instanceof BeanContext beanContext
                ? beanContext.getClassLoader()
                : AnnotatedMethodRouteBuilder.class.getClassLoader();
            this.precompiledRoutes = byController(SoftServiceLoader.load(PrecompiledHttpRoutesDefinition.class, classLoader).collectAll());
        } else {
            this.precompiledRoutes = Map.of();
        }
    }

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param definitions            The precompiled routes to use
     */
    AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator,
                                UriNamingStrategy uriNamingStrategy,
                                ConversionService conversionService,
                                List<PrecompiledHttpRoutesDefinition> definitions) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.precompiledRoutes = byController(definitions);
    }

    private static Map<String, List<PrecompiledRoute>> byController(List<PrecompiledHttpRoutesDefinition> definitions) {
        Map<String, List<PrecompiledRoute>> routes = new HashMap<>();
        for (PrecompiledHttpRoutesDefinition definition : definitions) {
            for (String controllerType : definition.controllerTypes()) {
                routes.putIfAbsent(controllerType, new ArrayList<>());
            }
            for (PrecompiledRoute route : definition.routes()) {
                routes.computeIfAbsent(route.controllerType(), k -> new ArrayList<>()).add(route);
            }
        }
        return routes;
    }

    /**
     * The routes of the enabled controllers whose routes are precompiled. They are built when first used.
     *
     * @return The routes
     */
    List<LazyUriRouteInfo> precompiledRouteInfos() {
        if (precompiledControllers.isEmpty()) {
            return List.of();
        }
        List<LazyUriRouteInfo> infos = new ArrayList<>();
        for (PrecompiledController controller : precompiledControllers.values()) {
            BeanDefinition<?> beanDefinition = controller.beanDefinition();
            for (PrecompiledRoute route : controller.routes()) {
                if (route.port() > -1) {
                    // the router collects the exposed ports when it is created
                    UriRouteInfo<Object, Object> info = buildPrecompiledRoute(beanDefinition, route);
                    infos.add(new LazyUriRouteInfo(route, () -> info));
                } else {
                    infos.add(new LazyUriRouteInfo(route, () -> buildPrecompiledRoute(beanDefinition, route)));
                }
            }
        }
        return infos;
    }

    @SuppressWarnings("unchecked")
    private UriRouteInfo<Object, Object> buildPrecompiledRoute(BeanDefinition<?> beanDefinition, PrecompiledRoute precompiledRoute) {
        ExecutableMethod<?, ?> method = findMethod(beanDefinition, precompiledRoute);
        MethodExecutionHandle<Object, Object> handle;
        if (precompiledRoute.declaringTypeTarget()) {
            handle = executionHandleLocator.findExecutionHandle((Class<Object>) method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes())
                .orElseThrow(() -> new RoutingException("No such route: " + method.getDeclaringType().getName() + "." + method.getMethodName()));
        } else {
            handle = (MethodExecutionHandle<Object, Object>) executionHandleLocator.createExecutionHandle(beanDefinition, (ExecutableMethod<Object, Object>) method);
        }
        DefaultUriRoute route = new DefaultUriRoute(
            HttpMethod.valueOf(precompiledRoute.httpMethod()),
            precompiledRoute.uri(),
            List.of(MediaType.APPLICATION_JSON_TYPE),
            handle,
            precompiledRoute.httpMethodName(),
            conversionService
        );
        if (precompiledRoute.consumes() != null) {
            route.consumes(MediaType.of(precompiledRoute.consumes()));
        }
        if (precompiledRoute.produces() != null) {
            route.produces(MediaType.of(precompiledRoute.produces()));
        }
        if (precompiledRoute.implicitHead()) {
            route.markImplicitHead();
        }
        if (precompiledRoute.port() > -1) {
            route.exposedPort(precompiledRoute.port());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created precompiled Route: {}", route);
        }
        return route.toRouteInfo();
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> beanDefinition, PrecompiledRoute route) {
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (method.getMethodName().equals(route.methodName()) && hasArgumentTypes(method, route.argumentTypes())) {
                return method;
            }
        }
        throw new RoutingException("No such route: " + route.controllerType() + "." + route.methodName());
    }

    private static boolean hasArgumentTypes(ExecutableMethod<?, ?> method, String[] argumentTypes) {
        Class<?>[] types = method.getArgumentTypes();
        if (types.length != argumentTypes.length) {
            return false;
        }
        for (int i = 0; i < types.length; i++) {
            if (!types[i].getName().equals(argumentTypes[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext beanContext) {
        if (!precompiledRoutes.isEmpty()) {
            String typeName = beanDefinition.getBeanType().getName();
            List<PrecompiledRoute> routes = precompiledRoutes.get(typeName);
            if (routes != null && !precompiledControllers.containsKey(typeName)) {
                precompiledControllers.put(typeName, new PrecompiledController(beanDefinition, routes));
                processErrors(beanDefinition);
                return;
            }
        }
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

    private void processErrors(BeanDefinition<?> beanDefinition) {
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (method.getAnnotationTypeByStereotype(HttpMethodMapping.class).orElse(null) == Error.class) {
                processError(beanDefinition, method);
            }
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

    /**
     * An enabled controller whose routes are precompiled.
     *
     * @param beanDefinition The controller
     * @param routes         The routes
     */
    private record PrecompiledController(BeanDefinition<?> beanDefinition, List<PrecompiledRoute> routes) {
    }
}
