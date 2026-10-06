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
package io.micronaut.dev.http;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.Qualifier;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.BeanType;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.web.router.RouteBuilder;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The router of a context in development mode: it forwards to the application's router, the one the
 * context would inject without it ({@link io.micronaut.web.router.DefaultRouter} unless the
 * application replaced it), and swaps that router for a new one when the routes change within a
 * generation.
 * <p>The application's router builds its route table once, in its constructor, and the HTTP server, the
 * {@code RouteExecutor} and the {@code CorsFilter} keep the router they received. Recreating the
 * router would recreate them with it, which means stopping the server. In development mode they
 * receive this router instead, as it is the primary one, and a rebuild recreates the route builders and
 * the application's router behind it with {@link WatchableBeanContext#recreate(Object)}: nothing that
 * holds this router is touched, and the next request routes through the new table. Outside development
 * mode this bean does not exist, so a request is routed by the application's router directly, with no
 * indirection and no check.</p>
 * <p>The table is rebuilt when, within a generation:</p>
 * <ul>
 *     <li>a {@link RouteBuilder}, an {@link HttpRoutes}, a {@link Controller}, a {@link ServerFilter} or a
 *     {@link Filter} definition is registered or removed;</li>
 *     <li>the development runtime redefines, in place, a class that declares routes: an {@link HttpRoutes}
 *     or {@link RouteBuilder} bean, or the factory producing one, whose method bodies are what declares
 *     the routes. A controller method's body is not, as its route comes from its annotations, and a
 *     change of those restarts the generation.</li>
 * </ul>
 * <p>A restart needs none of this: the new generation's context builds a new router from its own classes.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Primary
@Singleton
@Requires(classes = Router.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(condition = DevRouter.NoPrimaryApplicationRouter.class)
public final class DevRouter implements Router {

    private static final Logger LOG = LoggerFactory.getLogger(DevRouter.class);

    private final BeanContext context;
    private final Object lock = new Object();
    private volatile @Nullable Router router;
    private volatile @Nullable List<Integer> defaultPorts;
    private volatile int rebuilds;

    /**
     * @param context The context
     */
    DevRouter(BeanContext context) {
        this.context = context;
        if (context instanceof WatchableBeanContext watchable) {
            // registered while this bean is created: the watches belong to it and close when it is destroyed
            watchable.watchDefinitions(Argument.of(RouteBuilder.class), null, this::onChange);
            watchable.watchDefinitions(Argument.of(HttpRoutes.class), null, this::onChange);
            watchable.watchDefinitions(Argument.OBJECT_ARGUMENT, Qualifiers.byStereotype(Controller.class), this::onChange);
            watchable.watchDefinitions(Argument.OBJECT_ARGUMENT, Qualifiers.byStereotype(ServerFilter.class), this::onChange);
            watchable.watchDefinitions(Argument.OBJECT_ARGUMENT, Qualifiers.byStereotype(Filter.class), this::onChange);
            watchable.watchClassChanges(this::onClassChange);
        }
    }

    /**
     * @return The router requests are routed by now
     */
    public Router current() {
        Router current = router;
        if (current != null) {
            return current;
        }
        synchronized (lock) {
            current = router;
            if (current == null) {
                current = resolve();
                router = current;
            }
            return current;
        }
    }

    /**
     * @return How many times the route table was rebuilt within this generation
     */
    public int rebuilds() {
        return rebuilds;
    }

    /**
     * Rebuilds the route table: the route builders the context holds are recreated, which processes the
     * controllers, the filters and the {@link HttpRoutes} beans again, then the application's router is
     * created on top of them and requests are routed by it. Should the rebuild fail, such as for a
     * route declared twice, requests are routed by a router the context can still build, or else by the previous
     * one, whose route table outlives its destruction.
     */
    public void rebuild() {
        if (!(context instanceof WatchableBeanContext watchable)) {
            return;
        }
        synchronized (lock) {
            Router previous = router;
            try {
                // the router depends on the builders: recreating one destroys the router, and the builders
                // recreated read the definitions and the code as they are now
                for (BeanRegistration<RouteBuilder> registration : new ArrayList<>(context.getActiveBeanRegistrations(RouteBuilder.class))) {
                    watchable.recreate(registration.getBean());
                }
                if (previous != null) {
                    // still held when no builder was, such as when every builder is a singleton registered at runtime
                    watchable.recreate(previous);
                }
                install(resolve());
                rebuilds++;
                LOG.debug("Rebuilt the route table in place");
            } catch (RuntimeException e) {
                LOG.warn("The routes could not be rebuilt in place: {}", e.getMessage(), e);
                // the builders recreated so far took the previous router with them: route through whatever the context
                // can build now, or else through the previous router, whose table is still the one it built
                try {
                    install(resolve());
                } catch (RuntimeException again) {
                    LOG.debug("No router can be built until the routes are fixed; the previous routes stay", again);
                }
            }
        }
    }

    /**
     * Routes requests through the given router, restricted to the ports the server gave for the routes that name none:
     * without them a router serves those routes on every listener, such as the management one.
     *
     * @param fresh The router
     */
    private void install(Router fresh) {
        List<Integer> ports = defaultPorts;
        if (ports != null) {
            fresh.applyDefaultPorts(ports);
        }
        router = fresh;
    }

    private <T> void onChange(BeanDefinitionChange<T> change) {
        if (change.initial() || change.added().isEmpty() && change.removed().isEmpty() || router == null) {
            // a router never asked for has nothing to rebuild: it is built from the definitions when first used
            return;
        }
        rebuild();
    }

    private void onClassChange(ClassChangeEvent change) {
        // a restart builds a new context, with a new router; only a change applied in place needs a rebuild
        if (change.strategy() != ReloadStrategy.RELOAD || router == null || change.changes().isEmpty()) {
            return;
        }
        Set<String> declaring = routeDeclaringClasses();
        for (ClassChange classChange : change.changes()) {
            String name = classChange.className();
            for (String type : declaring) {
                if (name.equals(type) || name.startsWith(type + "$")) {
                    rebuild();
                    return;
                }
            }
        }
    }

    /**
     * @return The classes whose code declares routes: the {@link HttpRoutes} and {@link RouteBuilder} beans and the
     * factories producing them, with the application's superclasses and interfaces of each, as a bean may inherit the
     * method that declares its routes
     */
    private Set<String> routeDeclaringClasses() {
        Set<String> names = new HashSet<>();
        for (Class<?> type : List.of(HttpRoutes.class, RouteBuilder.class)) {
            for (BeanDefinition<?> definition : context.getBeanDefinitions(type)) {
                names.add(definition.getBeanType().getName());
                addAncestors(definition.getBeanType(), names);
                definition.getDeclaringType().ifPresent(declaringType -> {
                    names.add(declaringType.getName());
                    addAncestors(declaringType, names);
                });
            }
            for (BeanRegistration<?> registration : context.getActiveBeanRegistrations(type)) {
                // a lambda's class is named after the class declaring it
                Class<?> beanClass = registration.getBean().getClass();
                String name = beanClass.getName();
                int lambda = name.indexOf("$$");
                names.add(lambda > 0 ? name.substring(0, lambda) : name);
                addAncestors(beanClass, names);
            }
        }
        return names;
    }

    /**
     * Adds the superclasses and interfaces of a type, but those of the JDK and the router's own types, such as
     * {@link HttpRoutes} itself. Which of the others belong to the application is not told by their package, as an
     * application may use any: a framework type is never redefined in place, so its name never matches a change.
     *
     * @param type  The type
     * @param names The names to add to
     */
    private static void addAncestors(Class<?> type, Set<String> names) {
        for (Class<?> ancestor : ClassUtils.resolveHierarchy(type)) {
            String name = ancestor.getName();
            if (ancestor != type
                && ancestor != Object.class
                && !name.startsWith("java.")
                && !name.startsWith("javax.")
                && !name.startsWith("jdk.")
                && !name.startsWith("sun.")
                && !name.startsWith("io.micronaut.web.router.")) {
                names.add(name);
            }
        }
    }

    /**
     * @return The router the context would inject without this one
     */
    private Router resolve() {
        // the context resolves it as it would without this router: @Primary, @Secondary and the rest still apply
        return context.getBean(Router.class, ApplicationRouter.INSTANCE);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> context) {
        return current().findAny(uri, context);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        return current().findAny(request);
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return current().getExposedPorts();
    }

    @Override
    public void applyDefaultPorts(List<Integer> ports) {
        defaultPorts = List.copyOf(ports);
        current().applyDefaultPorts(ports);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        return current().find(httpMethod, uri, context);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, URI uri, @Nullable HttpRequest<?> context) {
        return current().find(httpMethod, uri, context);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        return current().find(request);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        return current().find(request, uri);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        return current().findAllClosest(request);
    }

    @Override
    public <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        return current().findClosest(request);
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return current().uriRoutes();
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        return current().route(httpMethod, uri);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
        return current().route(status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, HttpStatus status) {
        return current().route(originatingClass, status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Throwable error) {
        return current().route(error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, Throwable error) {
        return current().route(originatingClass, error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(Class<?> originatingClass, Throwable error, HttpRequest<?> request) {
        return current().findErrorRoute(originatingClass, error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(Throwable error, HttpRequest<?> request) {
        return current().findErrorRoute(error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, HttpStatus status, HttpRequest<?> request) {
        return current().findStatusRoute(originatingClass, status, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, int statusCode, HttpRequest<?> request) {
        return current().findStatusRoute(originatingClass, statusCode, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(HttpStatus status, HttpRequest<?> request) {
        return current().findStatusRoute(status, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(int statusCode, HttpRequest<?> request) {
        return current().findStatusRoute(statusCode, request);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request) {
        return current().findFilters(request);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch) {
        return current().findFilters(request, routeMatch);
    }

    @Override
    public List<GenericHttpFilter> findPreMatchingFilters(HttpRequest<?> request) {
        return current().findPreMatchingFilters(request);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> GET(CharSequence uri) {
        return current().GET(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> POST(CharSequence uri) {
        return current().POST(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PUT(CharSequence uri) {
        return current().PUT(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PATCH(CharSequence uri) {
        return current().PATCH(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> QUERY(CharSequence uri) {
        return current().QUERY(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> DELETE(CharSequence uri) {
        return current().DELETE(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(CharSequence uri) {
        return current().OPTIONS(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> HEAD(CharSequence uri) {
        return current().HEAD(uri);
    }

    @Override
    public String toString() {
        return "DevRouter{" + router + '}';
    }

    /**
     * Selects the routers other than this one.
     */
    private enum ApplicationRouter implements Qualifier<Router> {
        INSTANCE;

        @Override
        public <BT extends BeanType<Router>> Stream<BT> reduce(Class<Router> beanType, Stream<BT> candidates) {
            return candidates.filter(candidate -> !DevRouter.class.equals(candidate.getBeanType()));
        }
    }

    /**
     * Holds unless the application declares a primary router of its own, which this router would compete with for
     * every injection of a {@link Router}: the routes then reload by restarting only.
     */
    public static final class NoPrimaryApplicationRouter implements Condition {
        @Override
        public boolean matches(ConditionContext context) {
            BeanContext beanContext = context.getBeanContext();
            for (BeanDefinitionReference<Object> reference : beanContext.getBeanDefinitionReferences()) {
                // the requirements of each such router alone, which never involve this one
                if (reference.isPrimary() && reference.isPresent() && reference.isCandidateBean(Argument.of(Router.class))
                    && !DevRouter.class.equals(reference.getBeanType())
                    && reference.isEnabled(beanContext) && reference.load(beanContext).isEnabled(beanContext)) {
                    context.fail("The application declares a primary router: " + reference.getName());
                    return false;
                }
            }
            return true;
        }
    }
}
