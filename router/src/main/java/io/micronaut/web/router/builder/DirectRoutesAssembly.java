/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.DirectRouteSupport;
import io.micronaut.web.router.direct.HttpDirectRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * The {@link DirectRouteLookup} of the application: the direct routes of the
 * {@link HttpDirectRoutes} beans, declared when the application starts, under the context path of
 * the server. They are not routes of the router: a server runtime that declares a
 * {@link DirectRouteSupport} bean looks them up before it creates the request. Without one, the
 * application fails to start, as the routes would never be answered, or would be answered as
 * ordinary routes, which the filters they skip would see. The executors of the routes are looked up when the routes are built: an
 * executor that does not exist fails the startup.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Context
@Singleton
@Requires(beans = HttpDirectRoutes.class)
final class DirectRoutesAssembly implements DirectRouteLookup {

    private final DirectRouteLookup routes;

    /**
     * @param beanLocator        Finds the clock of the time conditions and the executors of the routes
     * @param conversionService  Converts the path variables of the routes
     * @param routes             The beans that declare the direct routes
     * @param contextPath        The context path of the server
     * @param directRouteSupport The server runtimes that answer direct routes, empty if none does
     */
    DirectRoutesAssembly(BeanLocator beanLocator,
                         ConversionService conversionService,
                         List<HttpDirectRoutes> routes,
                         @Nullable @Value("${micronaut.server.context-path}") String contextPath,
                         List<DirectRouteSupport> directRouteSupport) {
        DefaultDirectRouteBuilder builder = new DefaultDirectRouteBuilder(uri -> RouteAssembly.underContextPath(contextPath, uri));
        List<HttpDirectRoutes> ordered = new ArrayList<>(routes);
        OrderUtil.sort(ordered);
        try {
            for (HttpDirectRoutes directRoutes : ordered) {
                // the messages of the routes not ended with a terminal name the bean
                builder.declaredBy(directRoutes.getClass());
                directRoutes.routes(builder);
            }
        } catch (RuntimeException | Error e) {
            // the routes are read now: a route a bean declares later would be dropped
            builder.discard();
            throw e;
        }
        // fails for a route that was not ended with a terminal
        List<DirectRouteDeclaration> declarations = builder.close();
        // looked up when a time condition is first evaluated, like the router does
        RouteConditionContext conditionContext = RouteConditionContext.lazy(() -> beanLocator.findBean(RouteConditionContext.class)
            .orElse(RouteConditionContext.fallback()));
        DirectRouteTable table = DirectRouteTable.build(declarations, conversionService, conditionContext,
            (executorName, route) -> executor(beanLocator, executorName, route));
        if (!table.isEmpty() && directRouteSupport.isEmpty()) {
            // never served as ordinary routes, which the filters they skip would see
            throw new ConfigurationException("The application declares direct routes, see HttpDirectRoutes, "
                + "but the server runtime does not answer them: a direct route is answered before the request is created, "
                + "without filters, which only a server that supports it, e.g. the Netty server, can do. "
                + "Declare the routes with respond(...) of an HttpRoutes bean instead, which the filters see");
        }
        this.routes = table;
    }

    private static Executor executor(BeanLocator beanLocator, String executorName, DirectRouteDeclaration route) {
        ExecutorSelector selector = beanLocator.findBean(ExecutorSelector.class).orElse(null);
        if (selector == null) {
            throw new ConfigurationException("No executor selector to find the executor " + executorName + " of the " + route);
        }
        return selector.select(executorName).orElseThrow(() -> new ConfigurationException(
            "No executor configured for name: " + executorName + ", of the " + route));
    }

    @Override
    public @Nullable HttpResponse<?> find(DirectRequest request, HttpResponseFactory responses) {
        return routes.find(request, responses);
    }

    @Override
    public @Nullable CompletionStage<@Nullable HttpResponse<?>> findAsync(DirectRequest request, HttpResponseFactory responses) {
        return routes.findAsync(request, responses);
    }

    @Override
    public boolean isEmpty() {
        return routes.isEmpty();
    }
}
