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
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpStatus;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.web.router.exceptions.RoutingException;
import org.particleframework.core.naming.conventions.TypeConvention;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.http.uri.UriMatchTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A DefaultRouteBuilder implementation for building roots
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DefaultRouteBuilder implements RouteBuilder {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultRouteBuilder.class);
    /**
     * A {@link org.particleframework.web.router.RouteBuilder.UriNamingStrategy} where by camel case conventions are used
     */
    public static final UriNamingStrategy CAMEL_CASE_NAMING_STRATEGY = new UriNamingStrategy() {
    };

    /**
     * A {@link org.particleframework.web.router.RouteBuilder.UriNamingStrategy} whereby hyphenated naming conventions are used
     */
    public static final UriNamingStrategy HYPHENATED_NAMING_STRATEGY = new UriNamingStrategy() {
        @Override
        public String resolveUri(Class type) {
            return '/' + TypeConvention.CONTROLLER.asHyphenatedName(type);
        }

        @Override
        public String resolveUri(String property) {
            if( StringUtils.isEmpty(property) ) return "/";
            if(property.charAt(0) != '/') {
                return '/' + NameUtils.hyphenate(property, true);
            }
            return property;
        }
    };

    static final Object NO_VALUE = new Object();
    protected final ExecutionHandleLocator executionHandleLocator;
    protected final UriNamingStrategy uriNamingStrategy;
    protected final ConversionService<?> conversionService;

    private DefaultUriRoute currentParentRoute = null;
    private List<UriRoute> uriRoutes = new ArrayList<>();
    private List<StatusRoute> statusRoutes = new ArrayList<>();
    private List<ErrorRoute> errorRoutes = new ArrayList<>();

    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator) {
        this(executionHandleLocator, CAMEL_CASE_NAMING_STRATEGY);
    }

    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        this(executionHandleLocator, uriNamingStrategy, ConversionService.SHARED);
    }

    public DefaultRouteBuilder(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy, ConversionService<?> conversionService) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = uriNamingStrategy;
        this.conversionService = conversionService;
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
    public StatusRoute status(HttpStatus status, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultStatusRoute statusRoute = new DefaultStatusRoute(status, executableHandle, conversionService);
        this.statusRoutes.add(statusRoute);
        return statusRoute;
    }


    @Override
    public ErrorRoute error(Class originatingClass, Class<? extends Throwable> error, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultErrorRoute errorRoute = new DefaultErrorRoute(originatingClass, error, executableHandle, conversionService);
        this.errorRoutes.add(errorRoute);
        return errorRoute;
    }

    @Override
    public ErrorRoute error(Class<? extends Throwable> error, Class type, String method, Class[] parameterTypes) {
        Optional<MethodExecutionHandle<Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<Object> executableHandle = executionHandle.orElseThrow(() ->
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

    protected UriRoute buildRoute(HttpMethod httpMethod, String uri, Class<?> type, String method, Class... parameterTypes) {
        Optional<MethodExecutionHandle<Object>> executionHandle = executionHandleLocator.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultUriRoute route;
        if (currentParentRoute != null) {
            route = new DefaultUriRoute(httpMethod, currentParentRoute.uriMatchTemplate.nest(uri), executableHandle);
            currentParentRoute.nestedRoutes.add(route);
        } else {
            route = new DefaultUriRoute(httpMethod, uri, executableHandle);
        }
        this.uriRoutes.add(route);
        return route;
    }

    abstract class AbstractRoute implements Route {

        protected final List<Predicate<HttpRequest>> conditions = new ArrayList<>();
        protected final MethodExecutionHandle targetMethod;
        protected final ConversionService<?> conversionService;
        protected Set<MediaType> acceptedMediaTypes;
        protected String bodyArgument;

        AbstractRoute(MethodExecutionHandle targetMethod, ConversionService<?> conversionService, Set<MediaType> mediaTypes) {
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
            this.acceptedMediaTypes = mediaTypes;
        }

        @Override
        public Route accept(MediaType... mediaTypes) {
            if(mediaTypes != null) {
                this.acceptedMediaTypes = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(mediaTypes)));
            }
            return this;
        }

        @Override
        public Route acceptAll() {
            this.acceptedMediaTypes = Collections.emptySet();
            return this;
        }

        @Override
        public Route where(Predicate<HttpRequest> condition) {
            if (condition != null) {
                conditions.add(condition);
            }
            return this;
        }

        @Override
        public Route body(String argument) {
            this.bodyArgument = argument;
            return this;
        }
    }

    class DefaultErrorRoute extends AbstractRoute implements ErrorRoute, Comparable<ErrorRoute> {

        private final Class<? extends Throwable> error;
        private final Class originatingClass;

        public DefaultErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this(null, error, targetMethod, conversionService);
        }

        public DefaultErrorRoute(Class originatingClass, Class<? extends Throwable> error, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            super(targetMethod, conversionService, Collections.emptySet());
            this.originatingClass = originatingClass;
            this.error = error;
        }

        @Override
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public Class<? extends Throwable> exceptionType() {
            return error;
        }

        @Override
        public <T> Optional<RouteMatch<T>> match(Class originatingClass, Throwable exception) {
            if (originatingClass == this.originatingClass) {
                return match(exception);
            }
            return Optional.empty();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(Throwable exception) {
            if (error.isInstance(exception)) {
                return Optional.of(new ErrorRouteMatch(exception, this, conversionService));
            }
            return Optional.empty();
        }

        @Override
        public ErrorRoute accept(MediaType... mediaType) {
            return this;
        }

        @Override
        public Route acceptAll() {
            return this;
        }

        @Override
        public ErrorRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public ErrorRoute where(Predicate<HttpRequest> condition) {
            return (ErrorRoute) super.where(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultErrorRoute that = (DefaultErrorRoute) o;

            if (error != null ? !error.equals(that.error) : that.error != null) return false;
            return originatingClass != null ? originatingClass.equals(that.originatingClass) : that.originatingClass == null;
        }

        @Override
        public int hashCode() {
            int result = error != null ? error.hashCode() : 0;
            result = 31 * result + (originatingClass != null ? originatingClass.hashCode() : 0);
            return result;
        }

        @Override
        public int compareTo(ErrorRoute o) {
            if (o == this) {
                return 0;
            }
            Class<? extends Throwable> thatExceptionType = o.exceptionType();
            Class<? extends Throwable> thisExceptionType = this.error;

            if (thisExceptionType == thatExceptionType) {
                return 0;
            } else if (thisExceptionType.isAssignableFrom(thatExceptionType)) {
                return 1;
            } else if (thatExceptionType.isAssignableFrom(thisExceptionType)) {
                return -1;
            }
            return -1;
        }
    }

    /**
     * Represents a route for an {@link HttpStatus} code
     */
    class DefaultStatusRoute extends AbstractRoute implements StatusRoute {

        private final HttpStatus status;

        public DefaultStatusRoute(HttpStatus status, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            super(targetMethod, conversionService, Collections.emptySet());
            this.status = status;
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Optional<RouteMatch<T>> match(HttpStatus status) {
            if (this.status == status) {
                return Optional.of(new StatusRouteMatch(status, this, conversionService));
            }
            return Optional.empty();
        }


        @Override
        public StatusRoute accept(MediaType... mediaType) {
            return this;
        }

        @Override
        public Route acceptAll() {
            return this;
        }

        @Override
        public StatusRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public StatusRoute where(Predicate<HttpRequest> condition) {
            return (StatusRoute) super.where(condition);
        }

        public HttpStatus getStatus() {
            return status;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DefaultStatusRoute that = (DefaultStatusRoute) o;

            return status == that.status;
        }

        @Override
        public int hashCode() {
            return status.hashCode();
        }
    }

    /**
     * The default route impl
     */
    class DefaultUriRoute extends AbstractRoute implements UriRoute {

        final HttpMethod httpMethod;
        final UriMatchTemplate uriMatchTemplate;
        final List<DefaultUriRoute> nestedRoutes = new ArrayList<>();

        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, MediaType.APPLICATION_JSON_TYPE, targetMethod);
        }

        DefaultUriRoute(HttpMethod httpMethod, CharSequence uriTemplate, MediaType mediaType, MethodExecutionHandle targetMethod) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), new HashSet<>(Collections.singletonList(mediaType)), targetMethod);
        }

        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, new HashSet<>(Collections.singletonList(MediaType.APPLICATION_JSON_TYPE)), targetMethod);
        }

        DefaultUriRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, Set<MediaType> mediaTypes, MethodExecutionHandle targetMethod) {
            super(targetMethod, ConversionService.SHARED, mediaTypes);
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
        }


        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(httpMethod);
            return builder.append(' ')
                    .append(uriMatchTemplate)
                    .append(" -> ")
                    .append(targetMethod.getDeclaringType().getSimpleName())
                    .append('#')
                    .append(targetMethod)
                    .append(" (")
                    .append(acceptedMediaTypes.stream().collect(Collectors.joining(",")))
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
        public UriRoute accept(MediaType... mediaTypes) {
            return (UriRoute) super.accept(mediaTypes);
        }

        @Override
        public UriRoute acceptAll() {
            return (UriRoute) super.acceptAll();
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
        public UriRoute where(Predicate<HttpRequest> condition) {
            return (UriRoute) super.where(condition);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Optional<UriRouteMatch> match(String uri) {
            Optional<UriMatchInfo> matchInfo = uriMatchTemplate.match(uri);
            return matchInfo.map((info) -> new DefaultUriRouteMatch(info, this, conversionService));
        }


    }

    class DefaultSingleRoute extends DefaultResourceRoute {

        DefaultSingleRoute(Map<HttpMethod, Route> resourceRoutes, DefaultUriRoute getRoute) {
            super(resourceRoutes, getRoute);
        }

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

    class DefaultResourceRoute implements ResourceRoute {

        private final Map<HttpMethod, Route> resourceRoutes;
        private final DefaultUriRoute getRoute;

        DefaultResourceRoute(Map<HttpMethod, Route> resourceRoutes, DefaultUriRoute getRoute) {
            this.resourceRoutes = resourceRoutes;
            this.getRoute = getRoute;
        }

        DefaultResourceRoute(Class type) {
            this.resourceRoutes = new LinkedHashMap<>();
            // GET /foo/1
            Map<HttpMethod, Route> routeMap = this.resourceRoutes;
            this.getRoute = buildGetRoute(type, routeMap);
            buildRemainingRoutes(type, routeMap);
        }

        @Override
        public ResourceRoute accept(MediaType... mediaTypes) {
            DefaultRouteBuilder.this.uriRoutes.remove(getRoute);
            Set<MediaType> newMediaTypes = new HashSet<>(Arrays.asList(mediaTypes));
            return accept(newMediaTypes);
        }

        protected ResourceRoute accept(Set<MediaType> newMediaTypes) {
            DefaultUriRoute getRoute = new DefaultUriRoute(this.getRoute.httpMethod, this.getRoute.uriMatchTemplate, newMediaTypes, this.getRoute.targetMethod);
            DefaultRouteBuilder.this.uriRoutes.add(getRoute);

            Map<HttpMethod, Route> newMap = new LinkedHashMap<>();
            this.resourceRoutes.forEach((key, value) -> {
                if (value != this.getRoute) {
                    DefaultUriRoute defaultRoute = (DefaultUriRoute) value;
                    DefaultRouteBuilder.this.uriRoutes.remove(defaultRoute);
                    DefaultUriRoute newRoute = new DefaultUriRoute(defaultRoute.httpMethod, defaultRoute.uriMatchTemplate, newMediaTypes, this.getRoute.targetMethod);
                    newMap.put(key, newRoute);
                    DefaultRouteBuilder.this.uriRoutes.add(defaultRoute);
                }
            });
            return newResourceRoute(newMap, getRoute);
        }

        @Override
        public Route acceptAll() {
            return accept(Collections.emptySet());
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
        public ResourceRoute where(Predicate<HttpRequest> condition) {
            for (Route route : resourceRoutes.values()) {
                route.where(condition);
            }
            return this;
        }

        @Override
        public ResourceRoute body(String argument) {
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

        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultUriRoute getRoute) {
            return new DefaultResourceRoute(newMap, getRoute);
        }

        protected DefaultUriRoute buildGetRoute(Class type, Map<HttpMethod, Route> routeMap) {
            DefaultUriRoute getRoute = (DefaultUriRoute) DefaultRouteBuilder.this.GET(type, ID);
            routeMap.put(
                    HttpMethod.GET, getRoute
            );
            return getRoute;
        }

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
