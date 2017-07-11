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


import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpRequest;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.inject.ReturnType;
import org.particleframework.web.router.exceptions.RoutingException;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.naming.conventions.TypeConvention;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.http.uri.UriMatchTemplate;
import org.particleframework.inject.Argument;
import org.particleframework.inject.ExecutableHandle;

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

    public static final UriNamingStrategy CAMEL_CASE_NAMING_STRATEGY = new UriNamingStrategy() {
    };
    public static final UriNamingStrategy HYPHENATED_NAMING_STRATEGY = new UriNamingStrategy() {
        @Override
        public String resolveUri(Class type) {
            return '/' + TypeConvention.CONTROLLER.asHyphenatedName(type);
        }
    };
    private static final Object NO_VALUE = new Object();

    private final ApplicationContext beanContext;
    private final UriNamingStrategy uriNamingStrategy;

    private DefaultRoute currentParentRoute = null;
    private List<DefaultRoute> builtRoutes = new ArrayList<>();

    public DefaultRouteBuilder(ApplicationContext beanContext) {
        this.beanContext = beanContext;
        this.uriNamingStrategy = CAMEL_CASE_NAMING_STRATEGY;
    }

    public DefaultRouteBuilder(ApplicationContext beanContext, UriNamingStrategy uriNamingStrategy) {
        this.beanContext = beanContext;
        this.uriNamingStrategy = uriNamingStrategy;
    }

    @Override
    public List<Route> getConstructedRoutes() {
        return Collections.unmodifiableList(builtRoutes);
    }

    @Override
    public UriNamingStrategy getUriNamingStrategy() {
        return uriNamingStrategy;
    }


    @Override
    public ResourceRoute resources(Class cls) {
        return new DefaultResourceRoute(cls);
    }

    public List<Route> getBuiltRoutes() {
        return Collections.unmodifiableList(builtRoutes);
    }

    @Override
    public ResourceRoute single(Class cls) {
        return new DefaultSingleRoute(cls);
    }

    @Override
    public Route status(int code, Class type, String method) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Route error(Class<? extends Throwable> error, Class type, String method) {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    public Route GET(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route GET(String uri, Class<?> type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.GET, uri, type, method, parameterTypes);
    }

    @Override
    public Route POST(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route POST(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.POST, uri, type, method, parameterTypes);
    }

    @Override
    public Route PUT(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route PUT(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PUT, uri, type, method, parameterTypes);
    }

    @Override
    public Route PATCH(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route PATCH(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.PATCH, uri, type, method, parameterTypes);
    }

    @Override
    public Route DELETE(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route DELETE(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.DELETE, uri, type, method, parameterTypes);
    }

    @Override
    public Route OPTIONS(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route OPTIONS(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.OPTIONS, uri, type, method, parameterTypes);
    }

    @Override
    public Route HEAD(String uri, Object target, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, target.getClass(), method, parameterTypes);
    }

    @Override
    public Route HEAD(String uri, Class type, String method, Class... parameterTypes) {
        return buildRoute(HttpMethod.HEAD, uri, type, method, parameterTypes);
    }

    protected Route buildRoute(HttpMethod httpMethod, String uri, Class<?> type, String method, Class... parameterTypes) {
        Optional<MethodExecutionHandle<Object>> executionHandle = beanContext.findExecutionHandle(type, method, parameterTypes);

        MethodExecutionHandle<Object> executableHandle = executionHandle.orElseThrow(() ->
                new RoutingException("No such route: " + type.getName() + "." + method)
        );

        DefaultRoute route;
        if (currentParentRoute != null) {
            route = new DefaultRoute(httpMethod, currentParentRoute.uriMatchTemplate.nest(uri), executableHandle);
            currentParentRoute.nestedRoutes.add(route);
        } else {
            route = new DefaultRoute(httpMethod, uri, executableHandle);
        }
        this.builtRoutes.add(route);
        return route;
    }

    /**
     * The default route impl
     */
    class DefaultRoute implements Route {

        private final HttpMethod httpMethod;
        private final MediaType mediaType;
        private final UriMatchTemplate uriMatchTemplate;
        private final List<DefaultRoute> nestedRoutes = new ArrayList<>();
        private final MethodExecutionHandle targetMethod;
        private final List<Predicate<HttpRequest>> conditions = new ArrayList<>();

        DefaultRoute(HttpMethod httpMethod, CharSequence uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, MediaType.JSON, targetMethod);
        }

        DefaultRoute(HttpMethod httpMethod, CharSequence uriTemplate, MediaType mediaType, MethodExecutionHandle targetMethod) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), mediaType, targetMethod);
        }

        DefaultRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MethodExecutionHandle targetMethod) {
            this(httpMethod, uriTemplate, MediaType.JSON, targetMethod);
        }

        DefaultRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MediaType mediaType, MethodExecutionHandle targetMethod) {
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
            this.mediaType = mediaType;
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
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public Route accept(MediaType mediaType) {
            DefaultRouteBuilder.this.builtRoutes.remove(this);
            DefaultRoute newRoute = new DefaultRoute(httpMethod, uriMatchTemplate, mediaType, targetMethod);
            DefaultRouteBuilder.this.builtRoutes.add(newRoute);
            return newRoute;
        }

        @Override
        public Route nest(Runnable nested) {
            DefaultRoute previous = DefaultRouteBuilder.this.currentParentRoute;
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
        public Optional<RouteMatch> match(String uri) {
            Optional<UriMatchInfo> matchInfo = uriMatchTemplate.match(uri);
            return matchInfo.map((info) -> new DefaultRouteMatch(info, targetMethod));
        }

        class DefaultRouteMatch implements RouteMatch<Object> {

            private final UriMatchInfo matchInfo;
            private final MethodExecutionHandle executableMethod;

            public DefaultRouteMatch(UriMatchInfo matchInfo, MethodExecutionHandle executableMethod) {
                this.matchInfo = matchInfo;
                this.executableMethod = executableMethod;
            }

            @Override
            public Class getDeclaringType() {
                return executableMethod.getDeclaringType();
            }

            @Override
            public Argument[] getArguments() {
                return executableMethod.getArguments();
            }

            @Override
            public Object invoke(Object... arguments) {
                ConversionService conversionService = beanContext.getConversionService();

                Argument[] targetArguments = executableMethod.getArguments();
                if (targetArguments.length == 0) {
                    return executableMethod.invoke();
                } else {
                    List argumentList = new ArrayList();
                    Map<String, Object> variables = getVariables();
                    Iterator<Object> valueIterator = variables.values().iterator();
                    int i = 0;
                    for (Argument targetArgument : targetArguments) {
                        String name = targetArgument.getName();
                        Object value = variables.get(name);
                        if (value != null) {
                            Optional<?> result = conversionService.convert(value, targetArgument.getType());
                            argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                        } else if (valueIterator.hasNext()) {
                            Optional<?> result = conversionService.convert(valueIterator.next(), targetArgument.getType());
                            argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                        } else if (i < arguments.length) {
                            Optional<?> result = conversionService.convert(arguments[i++], targetArgument.getType());
                            argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                        } else {
                            throw new IllegalArgumentException("Wrong number of arguments to method: " + executableMethod);
                        }
                    }
                    return executableMethod.invoke(argumentList.toArray());
                }
            }

            @Override
            public Object execute(Map argumentValues) {
                Argument[] targetArguments = executableMethod.getArguments();
                if (targetArguments.length == 0) {
                    return executableMethod.invoke();
                } else {
                    ConversionService conversionService = beanContext.getConversionService();
                    Map<String, Object> uriVariables = getVariables();
                    List argumentList = new ArrayList();
                    for (Argument argument : targetArguments) {
                        String name = argument.getName();
                        Object value = NO_VALUE;
                        if (uriVariables.containsKey(name)) {
                            value = uriVariables.get(name);
                        } else if (argumentValues.containsKey(name)) {
                            value = argumentValues.get(name);
                        }

                        if (value == NO_VALUE) {
                            throw new IllegalArgumentException("Required argument [" + argument + "] not specified");
                        } else {
                            Object finalValue = value;
                            Optional<?> result = conversionService.convert(finalValue, argument.getType());
                            argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Unable to convert value [" + finalValue + "] for argument: " + argument)));
                        }
                    }

                    return executableMethod.invoke(argumentList.toArray());
                }
            }

            @Override
            public String getUri() {
                return matchInfo.getUri();
            }

            @Override
            public Map<String, Object> getVariables() {
                return matchInfo.getVariables();
            }

            @Override
            public boolean test(HttpRequest request) {
                for (Predicate<HttpRequest> condition : conditions) {
                    if (!condition.test(request)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public ReturnType<Object> getReturnType() {
                return targetMethod.getReturnType();
            }
        }
    }


    class DefaultSingleRoute extends DefaultResourceRoute {

        DefaultSingleRoute(Map<HttpMethod, Route> resourceRoutes, DefaultRoute getRoute) {
            super(resourceRoutes, getRoute);
        }

        DefaultSingleRoute(Class type) {
            super(type);
        }

        @Override
        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultRoute getRoute) {
            return new DefaultSingleRoute(newMap, getRoute);
        }

        @Override
        protected DefaultRoute buildGetRoute(Class type, Map<HttpMethod, Route> routeMap) {
            DefaultRoute getRoute = (DefaultRoute) DefaultRouteBuilder.this.GET(type);
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
        private final DefaultRoute getRoute;

        DefaultResourceRoute(Map<HttpMethod, Route> resourceRoutes, DefaultRoute getRoute) {
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
        public ResourceRoute accept(MediaType mediaType) {
            DefaultRouteBuilder.this.builtRoutes.remove(getRoute);
            DefaultRoute getRoute = new DefaultRoute(this.getRoute.httpMethod, this.getRoute.uriMatchTemplate, mediaType, this.getRoute.targetMethod);
            DefaultRouteBuilder.this.builtRoutes.add(getRoute);

            Map<HttpMethod, Route> newMap = new LinkedHashMap<>();
            this.resourceRoutes.forEach((key, value) -> {
                if (value != this.getRoute) {
                    DefaultRoute defaultRoute = (DefaultRoute) value;
                    DefaultRouteBuilder.this.builtRoutes.remove(defaultRoute);
                    DefaultRoute newRoute = new DefaultRoute(defaultRoute.httpMethod, defaultRoute.uriMatchTemplate, mediaType, this.getRoute.targetMethod);
                    newMap.put(key, newRoute);
                    DefaultRouteBuilder.this.builtRoutes.add(defaultRoute);
                }
            });
            return newResourceRoute(newMap, getRoute);
        }

        @Override
        public ResourceRoute nest(Runnable nested) {
            DefaultRoute previous = DefaultRouteBuilder.this.currentParentRoute;
            DefaultRouteBuilder.this.currentParentRoute = getRoute;
            try {
                nested.run();
            } finally {
                DefaultRouteBuilder.this.currentParentRoute = previous;
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

        protected ResourceRoute newResourceRoute(Map<HttpMethod, Route> newMap, DefaultRoute getRoute) {
            return new DefaultResourceRoute(newMap, getRoute);
        }

        protected DefaultRoute buildGetRoute(Class type, Map<HttpMethod, Route> routeMap) {
            DefaultRoute getRoute = (DefaultRoute) DefaultRouteBuilder.this.GET(type, ID);
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
                    DefaultRouteBuilder.this.builtRoutes.remove(value);
                } else {
                    newMap.put(key, value);
                }
            });
            return newResourceRoute(newMap, getRoute);
        }
    }
}
