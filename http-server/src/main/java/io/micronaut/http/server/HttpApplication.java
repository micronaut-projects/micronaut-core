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
package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Starts an HTTP application whose routes are declared in code, typically in the {@code main} method.
 *
 * <p>The routes are functional routes, registered as {@link HttpRoutes} beans. They coexist with the
 * controllers and beans of the application, which are discovered as for {@link Micronaut#run(String...)}.</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     HttpApplication.run(args, routes ->
 *         routes.GET("/hello/{name}", (request, vars) -> HttpResponse.ok("Hello " + vars.getString("name"))));
 * }
 * }</pre>
 *
 * <p>Routes that need application beans receive a {@link BeanLocator}:</p>
 *
 * <pre>{@code
 * HttpApplication.run(args, (routes, beans) -> {
 *     ItemService items = beans.getBean(ItemService.class);
 *     routes.GET("/items/{id}", (request, vars) -> HttpResponse.ok(items.find(vars.getLong("id"))));
 * });
 * }</pre>
 *
 * <p>Use {@link #build(String...)} to configure the application before starting it, e.g. to choose the
 * server when several are on the classpath and to customize its configuration:</p>
 *
 * <pre>{@code
 * HttpApplication.build(args)
 *     .server(NettyEmbeddedServer.class)
 *     .configure(NettyHttpServerConfiguration.class, netty -> netty.setMaxHeaderSize(16384))
 *     .routes(routes -> ...)
 *     .start();
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class HttpApplication {

    private static final String SERVER_PORT = "micronaut.server.port";

    private final Micronaut micronaut;

    private HttpApplication(Micronaut micronaut) {
        this.micronaut = micronaut;
    }

    /**
     * Start an application with the given routes.
     *
     * @param args   The command line arguments
     * @param routes The routes
     * @return The running application context
     */
    public static ApplicationContext run(String[] args, HttpRoutes routes) {
        return build(args).routes(routes).start();
    }

    /**
     * Start an application with routes that use application beans.
     *
     * @param args   The command line arguments
     * @param routes The routes
     * @return The running application context
     */
    public static ApplicationContext run(String[] args, BeanRoutes routes) {
        return build(args).routes(routes).start();
    }

    /**
     * Start an application with the given routes on a server of the given type.
     *
     * @param server The type of the server, see {@link #server(Class)}
     * @param args   The command line arguments
     * @param routes The routes
     * @return The running application context
     */
    public static ApplicationContext run(Class<? extends EmbeddedServer> server, String[] args, HttpRoutes routes) {
        return build(args).server(server).routes(routes).start();
    }

    /**
     * Start building an application.
     *
     * @param args The command line arguments
     * @return The application builder
     */
    public static HttpApplication build(String... args) {
        return new HttpApplication(Micronaut.build(args));
    }

    /**
     * Add routes. Each call adds an {@link HttpRoutes} bean; the routes of all calls are combined.
     *
     * @param routes The routes
     * @return This builder
     */
    public HttpApplication routes(HttpRoutes routes) {
        Objects.requireNonNull(routes, "routes");
        micronaut.beanDefinitions(
            RuntimeBeanDefinition.builder(HttpRoutes.class, () -> routes)
                .exposedTypes(HttpRoutes.class)
                .build()
        );
        return this;
    }

    /**
     * Add routes that use application beans. The beans are looked up when the routes are declared, after the
     * application context has started.
     *
     * @param routes The routes
     * @return This builder
     */
    public HttpApplication routes(BeanRoutes routes) {
        Objects.requireNonNull(routes, "routes");
        micronaut.beanDefinitions(
            RuntimeBeanDefinition.builder(HttpRoutes.class, context -> new BeanLocatorRoutes(routes, Objects.requireNonNull(context.getInjectedBean(0))))
                .injectionPoint(Argument.of(BeanContext.class))
                .exposedTypes(HttpRoutes.class)
                .build()
        );
        return this;
    }

    /**
     * Set the port of the server, {@code micronaut.server.port}. Use {@code -1} for a random port.
     *
     * @param port The port
     * @return This builder
     */
    public HttpApplication port(int port) {
        return property(SERVER_PORT, port);
    }

    /**
     * Set a configuration property.
     *
     * @param name  The property name
     * @param value The value
     * @return This builder
     */
    public HttpApplication property(String name, Object value) {
        micronaut.properties(Map.of(name, value));
        return this;
    }

    /**
     * Start the server of a type, such as {@code NettyEmbeddedServer}, rather than the only server on the
     * classpath, see {@link Micronaut#embeddedApplication(Class)}. Only the chosen server is started.
     *
     * @param server The type of the server, a bean type
     * @return This builder
     */
    public HttpApplication server(Class<? extends EmbeddedServer> server) {
        micronaut.embeddedApplication(server);
        return this;
    }

    /**
     * Customize a bean once it is created, before it is used, e.g. the configuration of the server.
     *
     * @param type       The bean type
     * @param configurer Customizes the bean
     * @param <T>        The bean type
     * @return This builder
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> HttpApplication configure(Class<T> type, Consumer<? super T> configurer) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(configurer, "configurer");
        BeanCreatedEventListener<T> listener = event -> {
            T bean = event.getBean();
            configurer.accept(bean);
            return bean;
        };
        micronaut.beanDefinitions(
            RuntimeBeanDefinition.builder((Class<BeanCreatedEventListener>) (Class) BeanCreatedEventListener.class, () -> listener)
                .typeArguments(Argument.of(type))
                .exposedTypes(BeanCreatedEventListener.class)
                .build()
        );
        return this;
    }

    /**
     * Configure the underlying {@link Micronaut} launcher, for example its environments or property sources.
     *
     * @param configurer The configurer
     * @return This builder
     */
    public HttpApplication micronaut(Consumer<? super Micronaut> configurer) {
        configurer.accept(micronaut);
        return this;
    }

    /**
     * Start the application.
     *
     * @return The running application context
     */
    public ApplicationContext start() {
        return micronaut.start();
    }

    /**
     * Routes that use application beans.
     */
    @Experimental
    @FunctionalInterface
    public interface BeanRoutes {

        /**
         * Declare the routes.
         *
         * @param routes The route builder
         * @param beans  The beans of the application
         */
        void routes(HttpRouteBuilder routes, BeanLocator beans);
    }

    /**
     * The {@link HttpRoutes} bean of {@link BeanRoutes}.
     *
     * @param routes The routes
     * @param beans  The beans of the application
     */
    private record BeanLocatorRoutes(BeanRoutes routes, BeanLocator beans) implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder builder) {
            routes.routes(builder, beans);
        }
    }
}
