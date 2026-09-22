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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ImmediateExecutor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RouteCondition;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.filter.FilterOrder;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.builder.AsyncFormRequestHandler;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.AsyncRouteRequestFilter;
import io.micronaut.web.router.builder.AsyncRouteResponseFilter;
import io.micronaut.web.router.builder.BodyRequestHandler;
import io.micronaut.web.router.builder.DeclaredUriRoute;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.FormRequestHandler;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HandlerUriRoute;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.builder.RouteRequestFilter;
import io.micronaut.web.router.builder.RouteResponseFilter;
import io.micronaut.web.router.builder.StatusRouteHandler;
import io.micronaut.web.router.builder.StreamingFormRequestHandler;
import io.micronaut.web.router.exceptions.RoutingException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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

    private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};

    protected final ExecutionHandleLocator executionHandleLocator;
    protected final UriNamingStrategy uriNamingStrategy;
    protected final ConversionService conversionService;
    protected final Charset defaultCharset;
    private final @Nullable ExecutorSelector executorSelector;

    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;

    private @Nullable DefaultUriRoute currentParentRoute;
    private final List<UriRoute> uriRoutes = new ArrayList<>();
    private final List<StatusRoute> statusRoutes = new ArrayList<>();
    private final List<ErrorRoute> errorRoutes = new ArrayList<>();
    private final List<FilterRoute> filterRoutes = new ArrayList<>();
    private final Set<Integer> exposedPorts = new HashSet<>(5);
    private final List<DeclaredUriRoute> declaredRoutes = new ArrayList<>(0);
    private final List<DeclaredUriRoute> implicitHeadDeclaredRoutes = new ArrayList<>(0);

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
        if (executionHandleLocator instanceof ApplicationContext applicationContext) {
            Environment environment = applicationContext.getEnvironment();
            defaultCharset = environment.get("micronaut.application.default-charset", Charset.class, StandardCharsets.UTF_8);
            this.executorSelector = applicationContext.findBean(ExecutorSelector.class).orElse(null);
            this.messageBodyHandlerRegistry = applicationContext.findBean(MessageBodyHandlerRegistry.class).orElse(MessageBodyHandlerRegistry.EMPTY);
        } else {
            defaultCharset = StandardCharsets.UTF_8;
            this.executorSelector = null;
            this.messageBodyHandlerRegistry = MessageBodyHandlerRegistry.EMPTY;
        }
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return exposedPorts;
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
        return Collections.unmodifiableList(statusRoutes);
    }

    @Override
    public List<ErrorRoute> getErrorRoutes() {
        return Collections.unmodifiableList(errorRoutes);
    }

    @Override
    public List<UriRoute> getUriRoutes() {
        return Collections.unmodifiableList(uriRoutes);
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

        DefaultStatusRoute statusRoute = new DefaultStatusRoute(originatingClass, status, executableHandle, conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }

    @Override
    public StatusRoute status(HttpStatus status, Class<?> type, String method, Class<?>[] parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultStatusRoute statusRoute = new DefaultStatusRoute(status, executableHandle, conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }

    @Override
    public ErrorRoute error(Class<?> originatingClass, Class<? extends Throwable> error, Class<?> type, String method, Class<?>... parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultErrorRoute errorRoute = new DefaultErrorRoute(originatingClass, error, executableHandle, conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
    }

    @Override
    public ErrorRoute error(Class<? extends Throwable> error, Class<?> type, String method, Class<?>[] parameterTypes) {
        Optional<MethodExecutionHandle<Object, Object>> executionHandle = executionHandleLocator.findExecutionHandle((Class<Object>) type, method, parameterTypes);

        MethodExecutionHandle<Object, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultErrorRoute errorRoute = new DefaultErrorRoute(error, executableHandle, conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
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

    @Internal
    public HandlerUriRoute handle(HttpMethod method, String uri, RequestHandler handler) {
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(handler)));
    }

    @Internal
    public <B> HandlerUriRoute handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        // the body argument is annotated @Body
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(bodyType, handler)));
    }

    @Internal
    public HandlerUriRoute handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(handler)))
            .consumes(FORM_MEDIA_TYPES);
    }

    @Internal
    public HandlerUriRoute handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler) {
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(handler)))
            .consumes(FORM_MEDIA_TYPES);
    }

    @Internal
    public HandlerUriRoute handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler) {
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(handler)))
            .consumes(FORM_MEDIA_TYPES);
    }

    @Internal
    public <E extends Throwable> ErrorRoute error(Class<E> type, ErrorRouteHandler<E> handler) {
        DefaultErrorRoute errorRoute = new DefaultErrorRoute(type, handlerHandle(HandlerMethod.of(type, handler)), conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
    }

    @Internal
    public StatusRoute status(HttpStatus status, StatusRouteHandler handler) {
        DefaultStatusRoute statusRoute = new DefaultStatusRoute(status, handlerHandle(HandlerMethod.of(handler)), conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }

    @Internal
    public HandlerUriRoute handle(RouteDeclaration route, RequestHandler handler) {
        return declare(route, HandlerMethod.of(handler), null);
    }

    @Internal
    public <B> HandlerUriRoute handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return declare(route, HandlerMethod.of(bodyType, handler), null);
    }

    @Internal
    public HandlerUriRoute handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return declare(route, HandlerMethod.of(handler), null);
    }

    @Internal
    public HandlerUriRoute handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return declare(route, HandlerMethod.of(handler), FORM_MEDIA_TYPES);
    }

    @Internal
    public HandlerUriRoute handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler) {
        return declare(route, HandlerMethod.of(handler), FORM_MEDIA_TYPES);
    }

    @Internal
    public HandlerUriRoute handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler) {
        return declare(route, HandlerMethod.of(handler), FORM_MEDIA_TYPES);
    }

    /**
     * Bind a handler to a declared route: the route is built when the router first uses it.
     *
     * @param declaration The declared route
     * @param method      The handler
     * @param consumes    The media types the route consumes, or {@code null} for the default
     * @return The route
     */
    private HandlerUriRoute declare(RouteDeclaration declaration, HandlerMethod<?> method, MediaType @Nullable [] consumes) {
        HttpMethod httpMethod = declaration.httpMethod();
        String uri = declaration.uriTemplate();
        MethodExecutionHandle<Object, Object> handle = handlerHandle(method);
        if (currentParentRoute != null || !routeUri(uri).equals(uri)) {
            // nested, or under a context path: the keys of the declaration do not describe the route
            HandlerUriRoute route = (HandlerUriRoute) buildRoute(httpMethod.name(), httpMethod, uri, handle);
            return consumes == null ? route : (HandlerUriRoute) route.consumes(consumes);
        }
        DeclaredUriRoute route = new DeclaredUriRoute(
            declaration,
            () -> new DefaultUriRoute(httpMethod, uri, List.of(MediaType.APPLICATION_JSON_TYPE), handle, httpMethod.name(), conversionService),
            exposedPorts::add
        );
        if (consumes != null) {
            route.consumes(consumes);
        }
        declaredRoutes.add(route);
        return route;
    }

    /**
     * The routes that are built when the router first uses them: here, the handler functions
     * bound to declared routes, and their implicit {@code HEAD} routes. Their configuration is
     * fixed when this method is called.
     *
     * @return The routes
     */
    List<LazyUriRouteInfo> lazyRouteInfos() {
        if (declaredRoutes.isEmpty()) {
            return List.of();
        }
        List<LazyUriRouteInfo> infos = new ArrayList<>(declaredRoutes.size() + implicitHeadDeclaredRoutes.size());
        for (DeclaredUriRoute route : declaredRoutes) {
            route.fix();
            infos.add(new LazyUriRouteInfo(route.declaration(), route.declaration().httpMethod(), false, route::toRouteInfo));
        }
        for (DeclaredUriRoute route : implicitHeadDeclaredRoutes) {
            infos.add(new LazyUriRouteInfo(route.declaration(), HttpMethod.HEAD, true, route::implicitHeadRouteInfo));
        }
        return infos;
    }

    @Internal
    public HandlerUriRoute handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return (HandlerUriRoute) buildRoute(method.name(), method, uri, handlerHandle(HandlerMethod.of(handler)));
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handlerHandle(HandlerMethod<?> method) {
        return (MethodExecutionHandle<Object, Object>) method;
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
        DefaultUriRoute route;
        if (currentParentRoute != null) {
            route = new DefaultUriRoute(
                httpMethod,
                currentParentRoute.uriMatchTemplate.nest(uri),
                mediaTypes,
                executableHandle,
                httpMethodName,
                conversionService
            );
            currentParentRoute.nestedRoutes.add(route);
        } else {
            route = new DefaultUriRoute(httpMethod, routeUri(uri), mediaTypes, executableHandle, httpMethodName, conversionService);
        }

        this.uriRoutes.add(route);
        routeCreated(route);
        return route;
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
     * A URI template under a context path.
     *
     * @param contextPath The context path, e.g. the {@code micronaut.server.context-path} property
     * @param uri         The URI template
     * @return The template under the context path
     */
    @Internal
    protected static String underContextPath(@Nullable String contextPath, String uri) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return uri;
        }
        String prefix = contextPath.charAt(0) == '/' ? contextPath : '/' + contextPath;
        return UriTemplate.of(prefix).nest(uri).toString();
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
        List<DefaultUriRoute> getRoutes = new ArrayList<>();
        Set<UriMatchTemplate> headTemplates = new HashSet<>();
        for (UriRoute route : uriRoutes) {
            if (route instanceof DefaultUriRoute defaultUriRoute) {
                if (defaultUriRoute.httpMethod == HttpMethod.GET) {
                    getRoutes.add(defaultUriRoute);
                } else if (defaultUriRoute.httpMethod == HttpMethod.HEAD) {
                    headTemplates.add(defaultUriRoute.uriMatchTemplate);
                }
            }
        }
        for (DefaultUriRoute getRoute : getRoutes) {
            if (!headTemplates.contains(getRoute.uriMatchTemplate)
                && getRoute.targetMethod.booleanValue(Get.class, "headRoute").orElse(true)) {
                uriRoutes.add(getRoute.implicitHeadCopy());
            }
        }
        // declared routes, compared by their templates without building them
        Set<String> declaredHeads = new HashSet<>();
        for (UriMatchTemplate template : headTemplates) {
            declaredHeads.add(template.toString());
        }
        for (DeclaredUriRoute route : declaredRoutes) {
            if (route.declaration().httpMethod() == HttpMethod.HEAD) {
                declaredHeads.add(route.declaration().uriTemplate());
            }
        }
        for (DeclaredUriRoute route : declaredRoutes) {
            if (route.declaration().httpMethod() == HttpMethod.GET && !declaredHeads.contains(route.declaration().uriTemplate())
                && !implicitHeadDeclaredRoutes.contains(route)) {
                implicitHeadDeclaredRoutes.add(route);
            }
        }
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
     * Abstract class for base {@link MethodBasedRouteInfo}.
     */
    abstract static class AbstractRoute implements Route {
        protected final List<Predicate<HttpRequest<?>>> conditions = new ArrayList<>();
        protected final MethodExecutionHandle<Object, Object> targetMethod;
        protected final ConversionService conversionService;
        protected List<MediaType> consumesMediaTypes;
        protected List<MediaType> producesMediaTypes = List.of();
        protected @Nullable String bodyArgumentName;
        protected @Nullable Argument<?> bodyArgument;

        /**
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         * @param mediaTypes The media types
         */
        AbstractRoute(MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService, List<MediaType> mediaTypes) {
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
            this.consumesMediaTypes = mediaTypes;
            for (Argument<?> argument : targetMethod.getArguments()) {
                if (argument.getAnnotationMetadata().hasAnnotation(Body.class)) {
                    this.bodyArgument = argument;
                }
            }
        }

        @Override
        public Route consumes(MediaType... mediaTypes) {
            if (mediaTypes != null) {
                this.consumesMediaTypes = List.of(mediaTypes);
            }
            return this;
        }

        @Override
        public List<MediaType> getConsumes() {
            return consumesMediaTypes;
        }

        @Override
        public Route consumesAll() {
            this.consumesMediaTypes = Collections.emptyList();
            return this;
        }

        @Override
        public Route where(Predicate<HttpRequest<?>> condition) {
            if (condition != null) {
                conditions.add(condition);
            }
            return this;
        }

        @Override
        public Route body(String argument) {
            this.bodyArgumentName = argument;
            return this;
        }

        @Override
        public Route body(Argument<?> argument) {
            this.bodyArgument = argument;
            return this;
        }

        @Override
        public Route produces(MediaType... mediaType) {
            if (mediaType != null) {
                this.producesMediaTypes = List.of(mediaType);
            }
            return this;
        }

        @Override
        public List<MediaType> getProduces() {
            return producesMediaTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AbstractRoute that)) {
                return false;
            }
            return Objects.equals(consumesMediaTypes, that.consumesMediaTypes) &&
                    Objects.equals(producesMediaTypes, that.producesMediaTypes);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(consumesMediaTypes, producesMediaTypes);
        }
    }

    /**
     * Default Error Route.
     */
    final class DefaultErrorRoute extends AbstractRoute implements ErrorRoute {

        private final Class<? extends Throwable> error;
        private final @Nullable Class<?> originatingClass;

         /**
          * @param error The throwable

         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            this(null, error, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param error The throwable
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(@Nullable Class<?> originatingClass,
                                 Class<? extends Throwable> error,
                                 MethodExecutionHandle<Object, Object> targetMethod,
                                 ConversionService conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.error = error;
        }

        @Override
        public ErrorRouteInfo<Object, Object> toRouteInfo() {
            return new DefaultErrorRouteInfo<>(
                    originatingClass,
                    error,
                    targetMethod,
                    bodyArgumentName,
                    bodyArgument,
                    consumesMediaTypes,
                    producesMediaTypes,
                    conditions,
                    conversionService,
                    messageBodyHandlerRegistry);
        }

        @Override
        @Nullable
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public Class<? extends Throwable> exceptionType() {
            return error;
        }

        @Override
        public ErrorRoute consumes(MediaType... mediaType) {
            return (ErrorRoute) super.consumes(mediaType);
        }

        @Override
        public ErrorRoute produces(MediaType... mediaType) {
            return (ErrorRoute) super.produces(mediaType);
        }

        @Override
        public Route consumesAll() {
            super.consumesAll();
            return this;
        }

        @Override
        public ErrorRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public ErrorRoute where(Predicate<HttpRequest<?>> condition) {
            return (ErrorRoute) super.where(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            DefaultErrorRoute that = (DefaultErrorRoute) o;
            return error.equals(that.error) &&
                    Objects.equals(originatingClass, that.originatingClass);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(super.hashCode(), error, originatingClass);
        }

        @Override
        public String toString() {
            return ' ' + error.getSimpleName()
                    + " -> " + targetMethod.getDeclaringType().getSimpleName()
                    + '#' + targetMethod;
        }
    }

    /**
     * Represents a route for an {@link io.micronaut.http.HttpStatus} code.
     */
    final class DefaultStatusRoute extends AbstractRoute implements StatusRoute {

        private final int statusCode;
        @Nullable
        private final Class<?> originatingClass;

        /**
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(HttpStatus status, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            this(null, status, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(@Nullable Class<?> originatingClass, HttpStatus status, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.statusCode = status.getCode();
        }

        @Override
        public StatusRouteInfo<Object, Object> toRouteInfo() {
            return new DefaultStatusRouteInfo<>(
                    originatingClass,
                    statusCode,
                    targetMethod,
                    bodyArgumentName,
                    bodyArgument,
                    consumesMediaTypes,
                    producesMediaTypes,
                    conditions,
                    conversionService,
                    messageBodyHandlerRegistry
            );
        }

        @Override
        @Nullable
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public StatusRoute consumes(MediaType... mediaType) {
            return this;
        }

        @Override
        public Route consumesAll() {
            return this;
        }

        @Override
        public StatusRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public StatusRoute where(Predicate<HttpRequest<?>> condition) {
            return (StatusRoute) super.where(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DefaultStatusRoute that)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            return statusCode == that.statusCode &&
                    Objects.equals(originatingClass, that.originatingClass);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(super.hashCode(), statusCode, originatingClass);
        }
    }

    /**
     * The default route impl.
     */
    @Internal
    public final class DefaultUriRoute extends AbstractRoute implements HandlerUriRoute {
        final String httpMethodName;
        final HttpMethod httpMethod;
        final UriMatchTemplate uriMatchTemplate;
        final List<DefaultUriRoute> nestedRoutes = new ArrayList<>(2);
        private @Nullable Integer port;
        private @Nullable String executeOn;
        private boolean nonBlocking;
        private final List<GenericHttpFilter> requestFilters = new ArrayList<>(0);
        private final List<GenericHttpFilter> responseFilters = new ArrayList<>(0);
        private boolean implicitHead;
        private final RouteExecutorSelector executorSelector = new RouteExecutorSelector();

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, uriTemplate, MediaType.APPLICATION_JSON_TYPE, targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaType The Media type
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        MediaType mediaType,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), Collections.singletonList(mediaType), targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaTypes The Media types
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        List<MediaType> mediaTypes,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), mediaTypes, targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        UriMatchTemplate uriTemplate,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, uriTemplate, Collections.singletonList(MediaType.APPLICATION_JSON_TYPE), targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param mediaTypes The media types
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        UriMatchTemplate uriTemplate,
                        List<MediaType> mediaTypes,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            super(targetMethod, conversionService, mediaTypes);
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
            this.httpMethodName = httpMethodName;
            if (targetMethod.isPresent(RouteCondition.class, AnnotationMetadata.VALUE_MEMBER)) {
                AnnotationValue<RouteCondition> annotation = targetMethod.getAnnotation(RouteCondition.class);
                if (annotation instanceof EvaluatedAnnotationValue<RouteCondition>) {
                    where(request -> annotation.booleanValue().orElse(false));
                }
            }
        }

        @Override
        public UriRouteInfo<Object, Object> toRouteInfo() {
            DefaultUrlRouteInfo<Object, Object> routeInfo = new DefaultUrlRouteInfo<>(
                httpMethod,
                httpMethodName,
                uriMatchTemplate,
                defaultCharset,
                targetMethod,
                bodyArgumentName,
                bodyArgument,
                consumesMediaTypes,
                producesMediaTypes,
                // a copy: the route info must not change with the route it was built from
                List.copyOf(conditions),
                port,
                conversionService,
                executorSelector,
                messageBodyHandlerRegistry,
                implicitHead
            );
            routeInfo.routeFilters = routeFilters();
            return routeInfo;
        }

        /**
         * A {@code HEAD} copy of this finished route, marked as implicit.
         *
         * @return The copy
         */
        @Internal
        public DefaultUriRoute implicitHeadCopy() {
            DefaultUriRoute head = new DefaultUriRoute(HttpMethod.HEAD, uriMatchTemplate, consumesMediaTypes, targetMethod, HttpMethod.HEAD.name(), conversionService);
            head.conditions.clear();
            head.conditions.addAll(conditions);
            head.producesMediaTypes = producesMediaTypes;
            head.bodyArgumentName = bodyArgumentName;
            head.bodyArgument = bodyArgument;
            head.port = port;
            head.executeOn = executeOn;
            head.nonBlocking = nonBlocking;
            head.requestFilters.addAll(requestFilters);
            head.responseFilters.addAll(responseFilters);
            head.implicitHead = true;
            return head;
        }

        /**
         * Marks this route as an implicit {@code HEAD} route derived from a {@code @Get} mapping.
         *
         * @see UriRouteInfo#isImplicitHead()
         */
        void markImplicitHead() {
            this.implicitHead = true;
        }

        @Override
        public String getHttpMethodName() {
            return httpMethodName;
        }

        @Override
        public String toString() {
            return getHttpMethodName() + ' '
                    + uriMatchTemplate
                    + " -> " + targetMethod.getDeclaringType().getSimpleName()
                    + '#' + targetMethod.getName()
                    + " (" + String.join(",", consumesMediaTypes) + ')';
        }

        @Override
        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        @Override
        public UriRoute body(String argument) {
            return (UriRoute) super.body(argument);
        }

        @Override
        public HandlerUriRoute executeOn(String executorName) {
            this.executeOn = Objects.requireNonNull(executorName, "executorName");
            this.nonBlocking = false;
            return this;
        }

        @Override
        public HandlerUriRoute before(RouteRequestFilter filter) {
            return addRequestFilter(filter, null);
        }

        @Override
        public HandlerUriRoute before(String executorName, RouteRequestFilter filter) {
            return addRequestFilter(filter, executor(executorName));
        }

        @Override
        public HandlerUriRoute beforeAsync(AsyncRouteRequestFilter filter) {
            Objects.requireNonNull(filter, "filter");
            requestFilters.add(GenericHttpFilter.createAsyncRouteRequestFilter(filter::filter));
            return this;
        }

        @Override
        public HandlerUriRoute after(RouteResponseFilter filter) {
            return addResponseFilter(filter, null);
        }

        @Override
        public HandlerUriRoute after(String executorName, RouteResponseFilter filter) {
            return addResponseFilter(filter, executor(executorName));
        }

        @Override
        public HandlerUriRoute afterAsync(AsyncRouteResponseFilter filter) {
            Objects.requireNonNull(filter, "filter");
            responseFilters.add(GenericHttpFilter.createAsyncRouteResponseFilter(filter::filter));
            return this;
        }

        private HandlerUriRoute addRequestFilter(RouteRequestFilter filter, @Nullable Supplier<Executor> executor) {
            Objects.requireNonNull(filter, "filter");
            requestFilters.add(GenericHttpFilter.createRouteRequestFilter(request -> {
                try {
                    return filter.filter(request);
                } catch (Exception e) {
                    return ExceptionUtils.sneakyThrow(e);
                }
            }, executor));
            return this;
        }

        private HandlerUriRoute addResponseFilter(RouteResponseFilter filter, @Nullable Supplier<Executor> executor) {
            Objects.requireNonNull(filter, "filter");
            responseFilters.add(GenericHttpFilter.createRouteResponseFilter((request, response) -> {
                try {
                    filter.filter(request, response);
                } catch (Exception e) {
                    ExceptionUtils.sneakyThrow(e);
                }
            }, executor));
            return this;
        }

        /**
         * The named executor, looked up when a filter first runs on it.
         *
         * @param executorName The name of the executor
         * @return The executor
         */
        private Supplier<Executor> executor(String executorName) {
            Objects.requireNonNull(executorName, "executorName");
            return SupplierUtil.memoized(() -> {
                ExecutorSelector selector = DefaultRouteBuilder.this.executorSelector;
                if (selector == null) {
                    throw new IllegalStateException("No executor selector to find executor: " + executorName);
                }
                return selector.select(executorName).orElseThrow(() -> new SchedulerConfigurationException(
                    targetMethod.getExecutableMethod(), "No executor configured for name: " + executorName));
            });
        }

        /**
         * @return The filters of the route in the order the filter chain runs them: the request
         * filters as declared, then the response filters in reverse, as response filters run from
         * the last to the first
         */
        List<GenericHttpFilter> routeFilters() {
            if (requestFilters.isEmpty() && responseFilters.isEmpty()) {
                return List.of();
            }
            List<GenericHttpFilter> filters = new ArrayList<>(requestFilters.size() + responseFilters.size());
            filters.addAll(requestFilters);
            filters.addAll(responseFilters.reversed());
            return List.copyOf(filters);
        }

        @Override
        public HandlerUriRoute nonBlocking() {
            this.nonBlocking = true;
            this.executeOn = null;
            return this;
        }

        @Override
        public UriRoute exposedPort(int port) {
            this.port = port;
            where(httpRequest -> httpRequest.getServerAddress().getPort() == port);
            DefaultRouteBuilder.this.exposedPorts.add(port);
            return this;
        }

        @Override
        public @Nullable Integer getPort() {
            return port;
        }

        @Override
        public UriRoute consumes(MediaType... mediaTypes) {
            return (UriRoute) super.consumes(mediaTypes);
        }

        @Override
        public UriRoute produces(MediaType... mediaType) {
            return (UriRoute) super.produces(mediaType);
        }

        @Override
        public UriRoute consumesAll() {
            return (UriRoute) super.consumesAll();
        }

        @Override
        public UriRoute nest(Runnable nested) {
            DefaultUriRoute previous = DefaultRouteBuilder.this.currentParentRoute;
            DefaultRouteBuilder.this.currentParentRoute = this;
            try {
                nested.run();
            } finally {
                DefaultRouteBuilder.this.currentParentRoute = previous;
            }
            return this;
        }

        @Override
        public UriRoute where(Predicate<HttpRequest<?>> condition) {
            return (UriRoute) super.where(condition);
        }

        @Override
        public UriMatchTemplate getUriMatchTemplate() {
            return this.uriMatchTemplate;
        }

        @Override
        public int compareTo(UriRoute o) {
            return uriMatchTemplate.compareTo(o.getUriMatchTemplate());
        }

        private final class RouteExecutorSelector implements ExecutorSelector {
            @Override
            public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection) {
                // like @ExecuteOn and @NonBlocking on the method
                String name = executeOn;
                if (name != null) {
                    return Optional.of(select(name).orElseThrow(() -> new SchedulerConfigurationException(
                        targetMethod.getExecutableMethod(), "No executor configured for name: " + name)));
                }
                if (nonBlocking && threadSelection == ThreadSelection.AUTO) {
                    return Optional.empty();
                }
                if (DefaultRouteBuilder.this.executorSelector != null) {
                    return DefaultRouteBuilder.this.executorSelector.select(targetMethod.getExecutableMethod(), threadSelection);
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public Optional<ExecutorService> select(String name) {
                if (DefaultRouteBuilder.this.executorSelector != null) {
                    return DefaultRouteBuilder.this.executorSelector.select(name);
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public Executor selectExecutor(@Nullable MethodReference<?, ?> method, ThreadSelectionConfiguration configuration) {
                if (executeOn != null || nonBlocking) {
                    // selects with the route's choice, see select(MethodReference, ThreadSelection)
                    return ExecutorSelector.super.selectExecutor(method, configuration);
                }
                if (DefaultRouteBuilder.this.executorSelector != null) {
                    return DefaultRouteBuilder.this.executorSelector.selectExecutor(method, configuration);
                } else {
                    return ImmediateExecutor.INSTANCE;
                }
            }
        }
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
            DefaultUriRoute previous = DefaultRouteBuilder.this.currentParentRoute;
            DefaultRouteBuilder.this.currentParentRoute = getRoute;
            try {
                nested.run();
            } finally {
                DefaultRouteBuilder.this.currentParentRoute = previous;
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
                    DefaultRouteBuilder.this.uriRoutes.remove(value);
                } else {
                    newMap.put(key, value);
                }
            });
            return newResourceRoute(newMap, getRoute);
        }
    }
}
