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

import io.micronaut.context.BeanLocator;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.RouteAssembly.DefaultUriRoute;
import io.micronaut.web.router.exceptions.RoutingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A DefaultRouteBuilder implementation for building roots.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DefaultRouteBuilder implements RouteBuilder {

    /**
     * A {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} whereby camel case conventions are used.
     */
    public static final UriNamingStrategy CAMEL_CASE_NAMING_STRATEGY = new UriNamingStrategy() {
    };

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultRouteBuilder.class);

    protected final ExecutionHandleLocator executionHandleLocator;
    protected final UriNamingStrategy uriNamingStrategy;
    protected final ConversionService conversionService;
    protected final Charset defaultCharset;

    /**
     * The routes of this builder, assembled like the routes of every route builder.
     */
    final RouteAssembly assembly;

    private final List<FilterRoute> filterRoutes = new ArrayList<>();

    /**
     * @param executionHandleLocator The execution handler locator
     */
    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator) {
        this(executionHandleLocator, CAMEL_CASE_NAMING_STRATEGY);
    }

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     */
    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        this(executionHandleLocator, uriNamingStrategy, ConversionService.SHARED);
    }

    /**
     * @param executionHandleLocator The execution handler locator
     * @param uriNamingStrategy The URI naming strategy
     * @param conversionService The conversion service
     */
    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService conversionService) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = uriNamingStrategy;
        this.conversionService = conversionService;
        this.assembly = new RouteAssembly(executionHandleLocator, conversionService, this::routeUri, this::routeCreated);
        this.defaultCharset = assembly.defaultCharset;
    }

    /**
     * A route builder of routes assembled elsewhere, e.g. the routes of the
     * {@link io.micronaut.web.router.builder.HttpRoutes} beans, so that the router receives them
     * with the other route builders. The URI templates of its routes are the ones of the assembly:
     * {@link #routeUri(String)} does not apply.
     *
     * @param executionHandleLocator The execution handler locator
     * @param conversionService      The conversion service
     * @param assembly               The assembled routes
     * @since 5.3.0
     */
    @Internal
    protected DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, ConversionService conversionService, RouteAssembly assembly) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = CAMEL_CASE_NAMING_STRATEGY;
        this.conversionService = conversionService;
        this.assembly = assembly;
        this.defaultCharset = assembly.defaultCharset;
    }

    @Override
    public Set<Integer> getExposedPorts() {
        // the set itself, as before the routes were assembled
        return assembly.exposedPorts;
    }

    @Override
    public List<FilterRoute> getFilterRoutes() {
        return filterRoutes;
    }

    @Override
    public FilterRoute addFilter(String pathPattern, BeanLocator beanLocator, BeanDefinition<? extends HttpFilter> definition) {
        FilterRoute fr = new DefaultFilterRoute(
            pathPattern,
            () -> GenericHttpFilter.createLegacyFilter(beanLocator.getBean(definition), new FilterOrder.Dynamic(definition.getOrder())),
            definition,
            false);
        filterRoutes.add(fr);
        return fr;
    }

    final FilterRoute addFilter(Supplier<GenericHttpFilter> internalFilter, AnnotationMetadata annotationMetadata, boolean isPreMatching) {
        FilterRoute fr = new DefaultFilterRoute(internalFilter, annotationMetadata, isPreMatching);
        filterRoutes.add(fr);
        return fr;
    }

    @Override
    public List<StatusRoute> getStatusRoutes() {
        return assembly.statusRoutes();
    }

    @Override
    public List<ErrorRoute> getErrorRoutes() {
        return assembly.errorRoutes();
    }

    @Override
    public List<UriRoute> getUriRoutes() {
        return assembly.uriRoutes();
    }

    @Override
    public UriNamingStrategy getUriNamingStrategy() {
        return uriNamingStrategy;
    }

    @Override
    public ResourceRoute resources(Class<?> cls) {
        return new DefaultResourceRoute(cls);
    }

    @Override
    public ResourceRoute single(Class<?> cls) {
        return new DefaultSingleRoute(cls);
    }

    @Override
    public StatusRoute status(Class<?> originatingClass, HttpStatus status, Class<?> type, String method, Class<?>... parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        return assembly.addStatusRoute(originatingClass, status, executableHandle);
    }

    @Override
    public StatusRoute status(HttpStatus status, Class<?> type, String method, Class<?>[] parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        return assembly.addStatusRoute(null, status, executableHandle);
    }

    @Override
    public ErrorRoute error(Class<?> originatingClass, Class<? extends Throwable> error, Class<?> type, String method, Class<?>... parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        return assembly.addErrorRoute(originatingClass, error, executableHandle);
    }

    @Override
    public ErrorRoute error(Class<? extends Throwable> error, Class<?> type, String method, Class<?>[] parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        return assembly.addErrorRoute(null, error, executableHandle);
    }

    @Override
    public UriRoute GET(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute GET(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute POST(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute POST(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute PUT(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute PUT(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute PATCH(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute PATCH(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute QUERY(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.QUERY, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute QUERY(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.QUERY, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute DELETE(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute DELETE(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute OPTIONS(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute OPTIONS(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute HEAD(String uri, Object target, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute HEAD(String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute TRACE(String uri, Object target, String method, Class<?>[] parameterTypes) {
        return buildRoute(HttpMethod.TRACE, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute TRACE(String uri, Class<?> type, String method, Class<?>[] parameterTypes) {
        return buildRoute(HttpMethod.TRACE, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute GET(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.GET, uri, beanDefinition, method);
    }

    @Override
    public UriRoute POST(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.POST, uri, beanDefinition, method);
    }

    @Override
    public UriRoute PUT(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.PUT, uri, beanDefinition, method);
    }

    @Override
    public UriRoute PATCH(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.PATCH, uri, beanDefinition, method);
    }

    @Override
    public UriRoute QUERY(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.QUERY, uri, beanDefinition, method);
    }

    @Override
    public UriRoute DELETE(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.DELETE, uri, beanDefinition, method);
    }

    @Override
    public UriRoute OPTIONS(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.OPTIONS, uri, beanDefinition, method);
    }

    @Override
    public UriRoute HEAD(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.HEAD, uri, beanDefinition, method);
    }

    @Override
    public UriRoute TRACE(String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(HttpMethod.TRACE, uri, beanDefinition, method);
    }

    /**
     * The routes that are built when the router first uses them. Their configuration is fixed
     * when this method is called.
     *
     * @return The routes
     */
    List<LazyUriRouteInfo> lazyRouteInfos() {
        return assembly.lazyRouteInfos();
    }

    /**
     * Build a route.
     *
     * @param httpMethod The HTTP method
     * @param uri The URI
     * @param type The type
     * @param method The method
     * @param parameterTypes Parameters
     *
     * @return an {@link UriRoute}
     */
    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, Class<?> type, String method, Class<?>... parameterTypes) {
        Optional<? extends MethodExecutionHandle<Object, Object>> executionHandle =
                executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        return buildRoute(httpMethod, uri, executableHandle);
    }

    /**
     * Build a route.
     *
     * @param httpMethod The HTTP method
     * @param uri The URI
     * @param executableHandle The executable handle
     *
     * @return an {@link UriRoute}
     */
    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, MethodExecutionHandle<Object, Object> executableHandle) {
        return buildRoute(httpMethod.name(), httpMethod, uri, executableHandle);
    }

    /**
     * Build a route.
     *
     * @param httpMethod The HTTP method
     * @param uri The URI
     * @param mediaTypes The media types
     * @param executableHandle The executable handle
     *
     * @since 4.2.0
     * @return an {@link UriRoute}
     */
    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle) {
        return buildRoute(httpMethod.name(), httpMethod, uri, mediaTypes, executableHandle);
    }

    private UriRoute buildRoute(String httpMethodName, HttpMethod httpMethod, String uri, MethodExecutionHandle<Object, Object> executableHandle) {
        return buildRoute(httpMethodName, httpMethod, uri, List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle);
    }

    private UriRoute buildRoute(String httpMethodName, HttpMethod httpMethod, String uri, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle) {
        return assembly.addRoute(httpMethodName, httpMethod, uri, mediaTypes, executableHandle);
    }

    /**
     * The URI template of a route that is not nested in another route.
     *
     * @param uri The URI template given to the builder
     * @return The URI template of the route
     */
    @Internal
    protected String routeUri(String uri) {
        return uri;
    }

    /**
     * Called for every URI route when it is created, before any further configuration of it.
     *
     * @param route The route
     */
    void routeCreated(DefaultUriRoute route) {
    }

    /**
     * Add an implicit {@code HEAD} route for every {@code GET} route that has no {@code HEAD} route
     * for the same URI, like {@link AnnotatedMethodRouteBuilder} does for {@code @Get} methods.
     * Each {@code HEAD} route is a copy of the finished {@code GET} route.
     */
    @Internal
    protected void addImplicitHeadRoutes() {
        assembly.addImplicitHeadRoutes();
    }

    private UriRoute buildBeanRoute(HttpMethod httpMethod, String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(httpMethod.name(), httpMethod, uri, beanDefinition, method);
    }

    /**
     * A special case that is required for non-standard http methods.
     * @param httpMethodName The name of method. For standard http methods matches {@link HttpMethod#name()}
     * @param httpMethod The http method. Is {@link HttpMethod#CUSTOM} for non-standard http methods.
     * @param uri The uri.
     * @param beanDefinition The definition of the bean.
     * @param method The method description
     * @return The uri route corresponding to the method.
     */
    protected UriRoute buildBeanRoute(String httpMethodName, HttpMethod httpMethod, String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        MethodExecutionHandle<Object, Object> executionHandle = (MethodExecutionHandle<Object, Object>) executionHandleLocator
                                                                .createExecutionHandle(beanDefinition, (ExecutableMethod<Object, Object>) method);
        return buildRoute(httpMethodName, httpMethod, uri, executionHandle);
    }

    /**
     * Define a single route.
     */
    final class DefaultSingleRoute extends DefaultResourceRoute {

        /**
         * @param resourceRoutes The resource routes
         * @param getRoute The default Uri route
         */
        DefaultSingleRoute(Map<HttpMethod, Route> resourceRoutes, DefaultUriRoute getRoute) {
            super(resourceRoutes, getRoute);
        }

        /**
         * @param type The class
         */
        DefaultSingleRoute(Class<?> type) {
            super(type);
        }

        @Override
        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultUriRoute getRoute) {
            return new DefaultSingleRoute(newMap, getRoute);
        }

        @Override
        protected DefaultUriRoute buildGetRoute(Class<?> type, Map<HttpMethod, Route> routeMap) {
            DefaultUriRoute getRoute = (DefaultUriRoute) DefaultRouteBuilder.this.GET(type);
            routeMap.put(
                HttpMethod.GET, getRoute
            );
            return getRoute;
        }

        @Override
        protected void buildRemainingRoutes(Class<?> type, Map<HttpMethod, Route> routeMap) {
            // POST /foo
            routeMap.put(
                HttpMethod.POST, DefaultRouteBuilder.this.POST(type)
            );
            // DELETE /foo
            routeMap.put(
                HttpMethod.DELETE, DefaultRouteBuilder.this.DELETE(type)
            );
            // PATCH /foo
            routeMap.put(
                HttpMethod.PATCH, DefaultRouteBuilder.this.PATCH(type)
            );
            // PUT /foo
            routeMap.put(
                HttpMethod.PUT, DefaultRouteBuilder.this.PUT(type)
            );
        }
    }

    /**
     * Default resource route.
     */
    class DefaultResourceRoute implements ResourceRoute {

        private final Map<HttpMethod, Route> resourceRoutes;
        private final DefaultUriRoute getRoute;

        /**
         * @param resourceRoutes The resource routes
         * @param getRoute The default Uri route
         */
        DefaultResourceRoute(Map<HttpMethod, Route> resourceRoutes, DefaultUriRoute getRoute) {
            this.resourceRoutes = resourceRoutes;
            this.getRoute = getRoute;
        }

        /**
         * @param type The class
         */
        DefaultResourceRoute(Class<?> type) {
            this.resourceRoutes = new LinkedHashMap<>();
            // GET /foo/1
            Map<HttpMethod, Route> routeMap = this.resourceRoutes;
            this.getRoute = buildGetRoute(type, routeMap);
            buildRemainingRoutes(type, routeMap);
        }

        @Override
        public RouteInfo<Object> toRouteInfo() {
            throw new IllegalStateException("Not implemented!");
        }

        @Override
        public ResourceRoute consumes(MediaType... mediaTypes) {
            if (mediaTypes != null) {
                for (Route route : resourceRoutes.values()) {
                    route.consumes(mediaTypes);
                }
            }
            return this;
        }

        @Override
        public Route consumesAll() {
            return consumes(MediaType.EMPTY_ARRAY);
        }

        @Override
        public ResourceRoute nest(Runnable nested) {
            DefaultUriRoute previous = assembly.currentParentRoute;
            assembly.currentParentRoute = getRoute;
            try {
                nested.run();
            } finally {
                assembly.currentParentRoute = previous;
            }
            return this;
        }

        @Override
        public ResourceRoute where(Predicate<HttpRequest<?>> condition) {
            for (Route route : resourceRoutes.values()) {
                route.where(condition);
            }
            return this;
        }

        @Override
        public ResourceRoute produces(MediaType... mediaType) {
            if (mediaType != null) {
                for (Route route : resourceRoutes.values()) {
                    route.produces(mediaType);
                }
            }
            return this;
        }

        @Override
        public ResourceRoute body(String argument) {
            return this;
        }

        @Override
        public Route body(Argument<?> argument) {
            return this;
        }

        @Override
        public ResourceRoute readOnly(boolean readOnly) {
            List<HttpMethod> excluded = Arrays.asList(HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.POST, HttpMethod.PUT);
            return handleExclude(excluded);
        }

        @Override
        public ResourceRoute exclude(HttpMethod... methods) {
            return handleExclude(Arrays.asList(methods));
        }

        /**
         * @param newMap New map info
         * @param getRoute The default route
         *
         * @return The {@link ResourceRoute}
         */
        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultUriRoute getRoute) {
            return new DefaultResourceRoute(newMap, getRoute);
        }

        /**
         * @param type The class
         * @param routeMap The route info
         *
         * @return The {@link DefaultUriRoute}
         */
        protected DefaultUriRoute buildGetRoute(Class<?> type, Map<HttpMethod, Route> routeMap) {
            DefaultUriRoute getRoute = (DefaultUriRoute) DefaultRouteBuilder.this.GET(type, ID);
            routeMap.put(
                HttpMethod.GET, getRoute
            );
            return getRoute;
        }

        /**
         * Build the remaining routes.
         *
         * @param type The class
         * @param routeMap The route info
         */
        protected void buildRemainingRoutes(Class<?> type, Map<HttpMethod, Route> routeMap) {
            // GET /foo
            routeMap.put(
                HttpMethod.GET, DefaultRouteBuilder.this.GET(type)
            );
            // POST /foo
            routeMap.put(
                HttpMethod.POST, DefaultRouteBuilder.this.POST(type)
            );
            // DELETE /foo/1
            routeMap.put(
                HttpMethod.DELETE, DefaultRouteBuilder.this.DELETE(type, ID)
            );
            // PATCH /foo/1
            routeMap.put(
                HttpMethod.PATCH, DefaultRouteBuilder.this.PATCH(type, ID)
            );
            // PUT /foo/1
            routeMap.put(
                HttpMethod.PUT, DefaultRouteBuilder.this.PUT(type, ID)
            );
        }

        private ResourceRoute handleExclude(List<HttpMethod> excluded) {
            var newMap = new LinkedHashMap<HttpMethod, Route>();
            this.resourceRoutes.forEach((key, value) -> {
                if (excluded.contains(key)) {
                    assembly.uriRoutes.remove(value);
                } else {
                    newMap.put(key, value);
                }
            });
            return newResourceRoute(newMap, getRoute);
        }
    }
}
