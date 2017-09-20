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
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpStatus;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.web.router.exceptions.RoutingException;
import org.particleframework.core.naming.conventions.TypeConvention;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.http.uri.UriMatchTemplate;

import java.util.*;
import java.util.function.Predicate;

/**
 * A DefaultRouteBuilder implementation for building roots
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DefaultRouteBuilder implements RouteBuilder {

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
    };


    static final Object NO_VALUE = new Object();


    private final ExecutionHandleLocator executionHandleLocator;
    private final UriNamingStrategy uriNamingStrategy;
    private final ConversionService<?> conversionService;

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

    class DefaultErrorRoute implements ErrorRoute, Comparable<ErrorRoute> {

        private final List<Predicate<HttpRequest>> conditions = new ArrayList<>();
        private final Class<? extends Throwable> error;
        private final Class originatingClass;
        private final MethodExecutionHandle targetMethod;
        private final ConversionService<?> conversionService;

        public DefaultErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this(null, error, targetMethod, conversionService);
        }

        public DefaultErrorRoute(Class originatingClass, Class<? extends Throwable> error, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this.originatingClass = originatingClass;
            this.error = error;
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
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
            if(originatingClass == this.originatingClass) {
                return match(exception);
            }
            return Optional.empty();
        }

        @Override
        public <T> Optional<RouteMatch<T>> match(Throwable exception) {
            if(error.isInstance(exception)) {
                return Optional.of(new ErrorRouteMatch(exception, targetMethod, conditions, conversionService));
            }
            return Optional.empty();
        }

        @Override
        public ErrorRoute accept(MediaType... mediaType) {
            return this;
        }

        @Override
        public ErrorRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public ErrorRoute where(Predicate<HttpRequest> condition) {
            if(condition != null) {
                conditions.add(condition);
            }
            return this;
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
            if(o == this) {
                return 0;
            }
            Class<? extends Throwable> thatExceptionType = o.exceptionType();
            Class<? extends Throwable> thisExceptionType = this.error;

            if(thisExceptionType == thatExceptionType) {
                return 0;
            }
            else if(thisExceptionType.isAssignableFrom(thatExceptionType)) {
                return 1;
            }
            else if(thatExceptionType.isAssignableFrom(thisExceptionType)) {
                return -1;
            }
            return -1;
        }
    }

    /**
     * Represents a route for an {@link HttpStatus} code
     */
    class DefaultStatusRoute implements StatusRoute {

        private final List<Predicate<HttpRequest>> conditions = new ArrayList<>();
        private final HttpStatus status;
        private final MethodExecutionHandle targetMethod;
        private final ConversionService<?> conversionService;

        public DefaultStatusRoute(HttpStatus status, MethodExecutionHandle targetMethod, ConversionService<?> conversionService) {
            this.status = status;
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
        }

        @Override
        public HttpStatus status() {
            return status;
        }

        @Override
        public <T> Optional<RouteMatch<T>> match(HttpStatus status) {
            if( this.status == status ) {
                return Optional.of(new StatusRouteMatch(status, targetMethod, conditions, conversionService));
            }
            return Optional.empty();
        }


        @Override
        public StatusRoute accept(MediaType... mediaType) {
            return this;
        }

        @Override
        public StatusRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public StatusRoute where(Predicate<HttpRequest> condition) {
            if(condition != null) {
                conditions.add(condition);
            }
            return this;
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
    class DefaultUriRoute implements UriRoute {

        private final HttpMethod httpMethod;
        private final Set<MediaType> acceptedMediaTypes;
        private final UriMatchTemplate uriMatchTemplate;
        private final List<DefaultUriRoute> nestedRoutes = new ArrayList<>();
        private final MethodExecutionHandle targetMethod;
        private final List<Predicate<HttpRequest>> conditions = new ArrayList<>();

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
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
            this.acceptedMediaTypes = mediaTypes;
            this.targetMethod = targetMethod;
        }


        @Override
        public String toString() {
            return httpMethod + " " + uriMatchTemplate + " -> " + targetMethod;
        }

        @Override
        public HttpMethod getHttpMethod() {
            return httpMethod;
        }


        @Override
        public Route accept(MediaType... mediaTypes) {
            DefaultRouteBuilder.this.uriRoutes.remove(this);
            DefaultUriRoute newRoute = new DefaultUriRoute(httpMethod, uriMatchTemplate, new HashSet<>(Arrays.asList(mediaTypes)), targetMethod);
            DefaultRouteBuilder.this.uriRoutes.add(newRoute);
            return newRoute;
        }

        @Override
        public Route nest(Runnable nested) {
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
        public Route where(Predicate<HttpRequest> condition) {
            conditions.add(condition);
            return this;
        }

        @Override
        public Optional<UriRouteMatch> match(String uri) {
            Optional<UriMatchInfo> matchInfo = uriMatchTemplate.match(uri);
            return matchInfo.map((info) -> new DefaultUriRouteMatch(httpMethod, info, targetMethod, conditions, acceptedMediaTypes, conversionService));
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
            DefaultUriRoute getRoute = new DefaultUriRoute(this.getRoute.httpMethod, this.getRoute.uriMatchTemplate, new HashSet<>(Arrays.asList(mediaTypes)), this.getRoute.targetMethod);
            DefaultRouteBuilder.this.uriRoutes.add(getRoute);

            Map<HttpMethod, Route> newMap = new LinkedHashMap<>();
            this.resourceRoutes.forEach((key, value) -> {
                if (value != this.getRoute) {
                    DefaultUriRoute defaultRoute = (DefaultUriRoute) value;
                    DefaultRouteBuilder.this.uriRoutes.remove(defaultRoute);
                    DefaultUriRoute newRoute = new DefaultUriRoute(defaultRoute.httpMethod, defaultRoute.uriMatchTemplate, new HashSet<>(Arrays.asList(mediaTypes)), this.getRoute.targetMethod);
                    newMap.put(key, newRoute);
                    DefaultRouteBuilder.this.uriRoutes.add(defaultRoute);
                }
            });
            return newResourceRoute(newMap, getRoute);
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
