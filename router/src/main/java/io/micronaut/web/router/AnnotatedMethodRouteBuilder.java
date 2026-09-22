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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.RouteAssembly.DefaultUriRoute;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import io.micronaut.web.router.spi.ControllerRoute;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * The slots of the usable route plans of controllers, by controller type name.
     */
    private final Map<String, PlannedController> plannedRoutes;
    /**
     * The enabled controllers whose routes are the slots of a route plan, in processing order.
     */
    private final Map<String, PlannedControllerBean> plannedControllers = new LinkedHashMap<>();

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        // the route compiler derives the routes of controllers like this class does with the
        // default naming strategy and no context path
        if (getClass() == AnnotatedMethodRouteBuilder.class
            && uriNamingStrategy.getClass() == HyphenatedUriNamingStrategy.class
            && "/".equals(uriNamingStrategy.resolveUri(""))) {
            ClassLoader classLoader = executionHandleLocator instanceof BeanContext beanContext
                ? beanContext.getClassLoader()
                : AnnotatedMethodRouteBuilder.class.getClassLoader();
            this.plannedRoutes = byController(RoutePlans.load(classLoader));
        } else {
            this.plannedRoutes = Map.of();
        }
    }

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param plans                  The route plans of controllers to use
     */
    AnnotatedMethodRouteBuilder(ExecutionHandleLocator executionHandleLocator,
                                UriNamingStrategy uriNamingStrategy,
                                ConversionService conversionService,
                                List<RoutePlan> plans) {
        super(executionHandleLocator, uriNamingStrategy, conversionService);
        this.plannedRoutes = byController(plans);
    }

    private static Map<String, PlannedController> byController(List<RoutePlan> plans) {
        Map<String, PlannedController> routes = new HashMap<>();
        for (RoutePlan plan : plans) {
            RouteSlot[] slots = plan.slots();
            if (!RoutePlans.usable(plan, slots)) {
                continue;
            }
            Map<String, List<RouteSlot>> byOwner = new LinkedHashMap<>();
            for (String owner : plan.owners()) {
                byOwner.put(owner, new ArrayList<>());
            }
            for (RouteSlot slot : slots) {
                ControllerRoute controller = slot.controller();
                if (controller != null) {
                    byOwner.computeIfAbsent(controller.ownerType(), k -> new ArrayList<>()).add(slot);
                }
            }
            for (Map.Entry<String, List<RouteSlot>> entry : byOwner.entrySet()) {
                PlannedController existing = routes.putIfAbsent(entry.getKey(), new PlannedController(plan, entry.getValue()));
                if (existing != null && LOG.isDebugEnabled()) {
                    // e.g. the plan of a controller and a plan linked from the same controller
                    LOG.debug("The routes of {} are in the route plans {} and {}: using {}", entry.getKey(), existing.plan().id(), plan.id(), existing.plan().id());
                }
            }
        }
        return routes;
    }

    @Override
    List<LazyUriRouteInfo> lazyRouteInfos() {
        List<LazyUriRouteInfo> declared = super.lazyRouteInfos();
        List<LazyUriRouteInfo> planned = plannedRouteInfos();
        if (declared.isEmpty()) {
            return planned;
        }
        List<LazyUriRouteInfo> infos = new ArrayList<>(declared);
        infos.addAll(planned);
        return infos;
    }

    /**
     * The routes of the enabled controllers whose routes are the slots of a route plan. They are
     * built when first used.
     *
     * @return The routes
     */
    List<LazyUriRouteInfo> plannedRouteInfos() {
        if (plannedControllers.isEmpty()) {
            return List.of();
        }
        List<LazyUriRouteInfo> infos = new ArrayList<>();
        for (PlannedControllerBean controller : plannedControllers.values()) {
            BeanDefinition<?> beanDefinition = controller.beanDefinition();
            RoutePlan plan = controller.routes().plan();
            for (RouteSlot slot : controller.routes().slots()) {
                ControllerRoute route = Objects.requireNonNull(slot.controller());
                if (route.port() > -1) {
                    // the router collects the exposed ports when it is created
                    UriRouteInfo<Object, Object> info = buildPlannedRoute(beanDefinition, slot, route);
                    infos.add(new LazyUriRouteInfo(plan, slot, () -> info));
                } else {
                    infos.add(new LazyUriRouteInfo(plan, slot, () -> buildPlannedRoute(beanDefinition, slot, route)));
                }
            }
        }
        return infos;
    }

    @SuppressWarnings("unchecked")
    private UriRouteInfo<Object, Object> buildPlannedRoute(BeanDefinition<?> beanDefinition, RouteSlot slot, ControllerRoute controllerRoute) {
        ExecutableMethod<?, ?> method = findMethod(beanDefinition, controllerRoute);
        MethodExecutionHandle<Object, Object> handle;
        if (controllerRoute.declaringTypeTarget()) {
            handle = executionHandleLocator.findExecutionHandle((Class<Object>) method.getDeclaringType(), method.getMethodName(), method.getArgumentTypes())
                .orElseThrow(() -> new RoutingException("No such route: " + method.getDeclaringType().getName() + "." + method.getMethodName()));
        } else {
            handle = (MethodExecutionHandle<Object, Object>) executionHandleLocator.createExecutionHandle(beanDefinition, (ExecutableMethod<Object, Object>) method);
        }
        DefaultUriRoute route = assembly.newRoute(
            slot.httpMethod(),
            slot.template().expression(),
            List.of(MediaType.APPLICATION_JSON_TYPE),
            handle,
            slot.httpMethodName()
        );
        String[] consumes = controllerRoute.consumes();
        if (consumes != null) {
            route.consumes(MediaType.of(consumes));
        }
        String[] produces = controllerRoute.produces();
        if (produces != null) {
            route.produces(MediaType.of(produces));
        }
        if (controllerRoute.implicitHead()) {
            route.markImplicitHead();
        }
        if (controllerRoute.port() > -1) {
            route.exposedPort(controllerRoute.port());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created Route of the slot {}: {}", slot.key(), route);
        }
        return route.toRouteInfo();
    }

    private static ExecutableMethod<?, ?> findMethod(BeanDefinition<?> beanDefinition, ControllerRoute route) {
        for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
            if (method.getMethodName().equals(route.methodName()) && hasArgumentTypes(method, route.argumentTypes())) {
                return method;
            }
        }
        throw new RoutingException("No such route: " + route.ownerType() + "." + route.methodName());
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
        if (!plannedRoutes.isEmpty()) {
            String typeName = beanDefinition.getBeanType().getName();
            PlannedController routes = plannedRoutes.get(typeName);
            if (routes != null && !plannedControllers.containsKey(typeName)) {
                plannedControllers.put(typeName, new PlannedControllerBean(beanDefinition, routes));
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
     * The slots of a route plan that are the routes of a controller.
     *
     * @param plan  The plan
     * @param slots The slots of the routes of the controller
     */
    private record PlannedController(RoutePlan plan, List<RouteSlot> slots) {
    }

    /**
     * An enabled controller whose routes are the slots of a route plan.
     *
     * @param beanDefinition The controller
     * @param routes         The slots
     */
    private record PlannedControllerBean(BeanDefinition<?> beanDefinition, PlannedController routes) {
    }
}
