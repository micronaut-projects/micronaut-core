/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataResolver;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.exceptions.RoutingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Qualifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
     * A {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} where by camel case conventions are used.
     */
    public static final UriNamingStrategy CAMEL_CASE_NAMING_STRATEGY = new UriNamingStrategy() {
    };

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultRouteBuilder.class);

    static final Object NO_VALUE = new Object();
    protected final ExecutionHandleLocator executionHandleLocator;
    protected final UriNamingStrategy uriNamingStrategy;
    protected final ConversionService<?> conversionService;
    protected final Charset defaultCharset;

    private DefaultUriRoute currentParentRoute = null;
    private List<UriRoute> uriRoutes = new ArrayList<>();
    private List<StatusRoute> statusRoutes = new ArrayList<>();
    private List<ErrorRoute> errorRoutes = new ArrayList<>();
    private List<FilterRoute> filterRoutes = new ArrayList<>();
    private Set<Integer> exposedPorts = new HashSet<>(5);

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
    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = uriNamingStrategy;
        this.conversionService = conversionService;
        if (executionHandleLocator instanceof ApplicationContext) {
            ApplicationContext applicationContext = (ApplicationContext) executionHandleLocator;
            Environment environment = applicationContext.getEnvironment();
            defaultCharset = environment.get("micronaut.application.default-charset", Charset.class, StandardCharsets.UTF_8);
        } else {
            defaultCharset = StandardCharsets.UTF_8;
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
    public FilterRoute addFilter(String pathPattern, Supplier<HttpFilter> filter) {
        DefaultFilterRoute route = new DefaultFilterRoute(
                pathPattern,
                filter,
                (AnnotationMetadataResolver) executionHandleLocator
        );
        filterRoutes.add(route);
        return route;
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
    public ResourceRoute resources(Class cls) {
        return new DefaultResourceRoute(cls);
    }

    @Override
    public ResourceRoute single(Class cls) {
        return new DefaultSingleRoute(cls);
    }

    @Override
    public StatusRoute status(Class originatingClass, HttpStatus status, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<?, Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<?, Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultStatusRoute statusRoute = new DefaultStatusRoute(originatingClass, status, executableHandle, conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }

    @Override
    public StatusRoute status(HttpStatus status, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<?, Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<?, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultStatusRoute statusRoute = new DefaultStatusRoute(status, executableHandle, conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }

    @Override
    public ErrorRoute error(Class originatingClass, Class<? extends Throwable> error, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<?, Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<?, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultErrorRoute errorRoute = new DefaultErrorRoute(originatingClass, error, executableHandle, conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
    }

    @Override
    public ErrorRoute error(Class<? extends Throwable> error, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<?, Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<?, Object> executableHandle = executionHandle.orElseThrow(() ->
            new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultErrorRoute errorRoute = new DefaultErrorRoute(error, executableHandle, conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
    }

    @Override
    public UriRoute GET(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute GET(String uri, Class<?> type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute POST(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute POST(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute PUT(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute PUT(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute PATCH(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute PATCH(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute DELETE(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute DELETE(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute OPTIONS(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute OPTIONS(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute HEAD(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute HEAD(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, type, method, parameterTypes);
    }

    @Override
    public UriRoute TRACE(String uri, Object target, String method, Class[] parameterTypes) {
        return buildRoute(HttpMethod.TRACE, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public UriRoute TRACE(String uri, Class type, String method, Class[] parameterTypes) {
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
    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, Class<?> type, String method, Class... parameterTypes) {
        Optional<? extends MethodExecutionHandle<?, Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<?, Object> executableHandle = executionHandle.orElseThrow(() ->
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
    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, MethodExecutionHandle<?, Object> executableHandle) {
        return buildRoute(httpMethod.name(), httpMethod, uri, executableHandle);
    }

    private UriRoute buildRoute(String httpMethodName, HttpMethod httpMethod, String uri, MethodExecutionHandle<?, Object> executableHandle) {
        UriRoute route;
        if (currentParentRoute != null) {
            route = new DefaultUriRoute(httpMethod, currentParentRoute.uriMatchTemplate.nest(uri), executableHandle, httpMethodName);
            currentParentRoute.nestedRoutes.add((DefaultUriRoute) route);
        } else {
            route = new DefaultUriRoute(httpMethod, uri, executableHandle, httpMethodName);
        }

        this.uriRoutes.add(route);
        return route;
    }

    private UriRoute buildBeanRoute(HttpMethod httpMethod, String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        return buildBeanRoute(httpMethod.name(), httpMethod, uri, beanDefinition,  method);
    }

    /**
     * A special case that is required for non standard http methods.
     * @param httpMethodName The name of method. For standard http methods matches {@link HttpMethod#name()}
     * @param httpMethod The http method. Is {@link HttpMethod#CUSTOM} for non standard http methods.
     * @param uri The uri.
     * @param beanDefinition The definition of the bean.
     * @param method The method description
     * @return The uri route corresponding to the method.
     */
    protected UriRoute buildBeanRoute(String httpMethodName, HttpMethod httpMethod, String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        io.micronaut.context.Qualifier<?> qualifier = beanDefinition.getAnnotationTypeByStereotype(Qualifier.class).map(aClass -> Qualifiers.byAnnotation(beanDefinition, aClass)).orElse(null);
        if (qualifier == null && beanDefinition.isIterable() && beanDefinition instanceof NameResolver) {
            qualifier = ((NameResolver) beanDefinition).resolveName()
                    .map(Qualifiers::byName).orElse(null);
        }
        MethodExecutionHandle<?, Object> executionHandle = executionHandleLocator.findExecutionHandle(beanDefinition.getBeanType(), qualifier, method.getMethodName(), method.getArgumentTypes())
                .orElseThrow(() -> new RoutingException("No such route: " + beanDefinition.getBeanType().getName() + "." + method));
        return buildRoute(httpMethodName, httpMethod, uri, executionHandle);
    }

    /**
     * Abstract class for base {@link MethodBasedRoute}.
     */
    abstract class AbstractRoute implements MethodBasedRoute, RouteInfo<Object> {
        protected final List<Predicate<HttpRequest<?>>> conditions = new ArrayList<>();
        protected final MethodExecutionHandle<?, ?> targetMethod;
        protected final ConversionService<?> conversionService;
        protected List<MediaType> consumesMediaTypes;
        protected List<MediaType> producesMediaTypes;
        protected String bodyArgumentName;
        protected Argument<?> bodyArgument;
        private final boolean isVoid;
        private final boolean suspended;
        private final boolean reactive;
        private final boolean single;
        private final boolean async;

        /**
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         * @param mediaTypes The media types
         */
        AbstractRoute(MethodExecutionHandle targetMethod, ConversionService<?> conversionService, List<MediaType> mediaTypes) {
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
            this.consumesMediaTypes = mediaTypes;

            MediaType[] types = MediaType.of(targetMethod.stringValues(Produces.class));
            if (ArrayUtils.isNotEmpty(types)) {
                this.producesMediaTypes = Arrays.asList(types);
            }
            types = MediaType.of(targetMethod.stringValues(Consumes.class));
            if (ArrayUtils.isNotEmpty(types)) {
                this.consumesMediaTypes = Arrays.asList(types);
            }
            suspended = targetMethod.getExecutableMethod().isSuspend();
            reactive = RouteInfo.super.isReactive();
            async = RouteInfo.super.isAsync();
            single = RouteInfo.super.isSingleResult();
            isVoid = RouteInfo.super.isVoid();
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return targetMethod.getAnnotationMetadata();
        }

        @Override
        public ReturnType<?> getReturnType() {
            return targetMethod.getReturnType();
        }

        @Override
        public boolean isSuspended() {
            return suspended;
        }

        @Override
        public boolean isReactive() {
            return reactive;
        }

        @Override
        public boolean isSingleResult() {
            return single;
        }

        @Override
        public boolean isAsync() {
            return async;
        }

        @Override
        public boolean isVoid() {
            return isVoid;
        }

        @Override
        public Route consumes(MediaType... mediaTypes) {
            if (mediaTypes != null) {
                this.consumesMediaTypes = Collections.unmodifiableList(Arrays.asList(mediaTypes));
            }
            return this;
        }

        @Override
        public List<MediaType> getConsumes() {
            if (consumesMediaTypes != null) {
                return consumesMediaTypes;
            } else {
                return Collections.emptyList();
            }
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
                this.producesMediaTypes = Arrays.asList(mediaType);
            }
            return this;
        }

        @Override
        public List<MediaType> getProduces() {
            if (producesMediaTypes != null) {
                return Collections.unmodifiableList(producesMediaTypes);
            } else {
                return DEFAULT_PRODUCES;
            }
        }

        @Override
        public MethodExecutionHandle getTargetMethod() {
            return this.targetMethod;
        }

        /**
         * Whether the route permits a request body.
         * @return True if the route permits a request body
         */
        protected boolean permitsRequestBody() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AbstractRoute)) {
                return false;
            }
            AbstractRoute that = (AbstractRoute) o;
            return Objects.equals(consumesMediaTypes, that.consumesMediaTypes) &&
                    Objects.equals(producesMediaTypes, that.producesMediaTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumesMediaTypes, producesMediaTypes);
        }
    }

    /**
     * Default Error Route.
     */
    class DefaultErrorRoute extends AbstractRoute implements ErrorRoute {

        private final Class<? extends Throwable> error;
        private final Class originatingClass;

        /**
         * @param error The throwable
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this(null, error, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param error The throwable
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(
                Class originatingClass, Class<? extends Throwable> error,
                MethodExecutionHandle targetMethod,
                ConversionService<?> conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.error = error;
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

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(Class originatingClass, Throwable exception) {
            if (originatingClass == this.originatingClass && error.isInstance(exception)) {
                return Optional.of(new ErrorRouteMatch(exception, this, conversionService));
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(Throwable exception) {
            if (originatingClass == null && error.isInstance(exception)) {
                return Optional.of(new ErrorRouteMatch(exception, this, conversionService));
            }
            return Optional.empty();
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
            return Objects.hash(super.hashCode(), error, originatingClass);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            return builder.append(' ')
                .append(error.getSimpleName())
                .append(" -> ")
                .append(targetMethod.getDeclaringType().getSimpleName())
                .append('#')
                .append(targetMethod)
                .toString();
        }
    }

    /**
     * Represents a route for an {@link io.micronaut.http.HttpStatus} code.
     */
    class DefaultStatusRoute extends AbstractRoute implements StatusRoute {

        private final HttpStatus status;
        private final Class originatingClass;

        /**
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(HttpStatus status, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this(null, status, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(Class originatingClass, HttpStatus status, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.status = status;
        }

        @Override
        @Nullable
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(Class originatingClass, HttpStatus status) {
            if (originatingClass == this.originatingClass && this.status == status) {
                return Optional.of(new StatusRouteMatch(status, this, conversionService));
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(HttpStatus status) {
            if (this.originatingClass == null && this.status == status) {
                return Optional.of(new StatusRouteMatch(status, this, conversionService));
            }
            return Optional.empty();
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

        /**
         * @return The {@link io.micronaut.http.HttpStatus}
         */
        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DefaultStatusRoute)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            DefaultStatusRoute that = (DefaultStatusRoute) o;
            return status == that.status &&
                    Objects.equals(originatingClass, that.originatingClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), status, originatingClass);
        }
    }

    /**
     * The default route impl.
     */
    class DefaultUriRoute extends AbstractRoute implements UriRoute {
        final String httpMethodName;
        final HttpMethod httpMethod;
        final UriMatchTemplate uriMatchTemplate;
        final List<DefaultUriRoute> nestedRoutes = new ArrayList<>(2);
        private Integer port;

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param targetMethod The target method execution handle
         */
        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, targetMethod, httpMethod.name());
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         */
        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MethodExecutionHandle targetMethod, String httpMethodName) {
            this(httpMethod, uriTemplate, MediaType.APPLICATION_JSON_TYPE, targetMethod, httpMethodName);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaType The Media type
         * @param targetMethod The target method execution handle
         */
        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MediaType mediaType, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, mediaType, targetMethod, httpMethod.name());
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaType The Media type
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         */
        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MediaType mediaType, MethodExecutionHandle targetMethod, String httpMethodName) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), Collections.singletonList(mediaType), targetMethod, httpMethodName);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param targetMethod The target method execution handle
         */
        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, targetMethod, httpMethod.name());
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         */
        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MethodExecutionHandle targetMethod, String httpMethodName) {
            this(httpMethod, uriTemplate, Collections.singletonList(MediaType.APPLICATION_JSON_TYPE), targetMethod, httpMethodName);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param mediaTypes The media types
         * @param targetMethod The target method execution handle
         */
        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, List<MediaType> mediaTypes, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, mediaTypes, targetMethod, httpMethod.name());
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param mediaTypes The media types
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         */
        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, List<MediaType> mediaTypes, MethodExecutionHandle targetMethod, String httpMethodName) {
            super(targetMethod, ConversionService.SHARED, mediaTypes);
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
            this.httpMethodName = httpMethodName;
        }

        @Override
        public String getHttpMethodName() {
            return httpMethodName;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(getHttpMethodName());
            return builder.append(' ')
                .append(uriMatchTemplate)
                .append(" -> ")
                .append(targetMethod.getDeclaringType().getSimpleName())
                .append('#')
                .append(targetMethod)
                .append(" (")
                .append(String.join(",", consumesMediaTypes))
                .append(" )")
                .toString();
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
        public UriRoute exposedPort(int port) {
            this.port = port;
            where(httpRequest -> httpRequest.getServerAddress().getPort() == port);
            DefaultRouteBuilder.this.exposedPorts.add(port);
            return this;
        }

        @Override
        public Integer getPort() {
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

        @SuppressWarnings("unchecked")
        @Override
        public Optional<UriRouteMatch> match(String uri) {
            Optional<UriMatchInfo> matchInfo = uriMatchTemplate.match(uri);
            return matchInfo.map((info) -> new DefaultUriRouteMatch(info, this, defaultCharset, conversionService));
        }

        @Override
        public UriMatchTemplate getUriMatchTemplate() {
            return this.uriMatchTemplate;
        }

        @Override
        public int compareTo(UriRoute o) {
            return uriMatchTemplate.compareTo(o.getUriMatchTemplate());
        }

        @Override
        protected boolean permitsRequestBody() {
            return HttpMethod.permitsRequestBody(httpMethod);
        }
    }

    /**
     * Define a single route.
     */
    class DefaultSingleRoute extends DefaultResourceRoute {

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
        DefaultSingleRoute(Class type) {
            super(type);
        }

        @Override
        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultUriRoute getRoute) {
            return new DefaultSingleRoute(newMap, getRoute);
        }

        @Override
        protected DefaultUriRoute buildGetRoute(Class type, Map<HttpMethod, Route> routeMap) {
            DefaultUriRoute getRoute = (DefaultUriRoute) DefaultRouteBuilder.this.GET(type);
            routeMap.put(
                HttpMethod.GET, getRoute
            );
            return getRoute;
        }

        @Override
        protected void buildRemainingRoutes(Class type, Map<HttpMethod, Route> routeMap) {
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
        DefaultResourceRoute(Class type) {
            this.resourceRoutes = new LinkedHashMap<>();
            // GET /foo/1
            Map<HttpMethod, Route> routeMap = this.resourceRoutes;
            this.getRoute = buildGetRoute(type, routeMap);
            buildRemainingRoutes(type, routeMap);
        }

        @Override
        public ResourceRoute consumes(MediaType... mediaTypes) {
            if (mediaTypes != null) {
                for (Route route : resourceRoutes.values()) {
                    route.produces(mediaTypes);
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
        protected DefaultUriRoute buildGetRoute(Class type, Map<HttpMethod, Route> routeMap) {
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
        protected void buildRemainingRoutes(Class type, Map<HttpMethod, Route> routeMap) {
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
            Map<HttpMethod, Route> newMap = new LinkedHashMap<>();
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
