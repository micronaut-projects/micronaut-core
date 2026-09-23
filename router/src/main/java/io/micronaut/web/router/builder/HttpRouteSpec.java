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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A route to a handler function, to configure after it was added with the {@link HttpRouteBuilder}.
 * A route on several HTTP methods configures all of them.
 *
 * <p>The filters of the route, see {@link RouteFilterSpec}, run after the application's filters and
 * the filters of the groups the route is declared in, closest to the route, and are resolved when
 * the route is built.</p>
 *
 * <p>The configuration of the route is read when the router is built, once the routes were
 * declared: configure the route where it is declared, in {@link HttpRoutes#routes(HttpRouteBuilder)}
 * or in the callback that builds a route table. A change made to a route kept after that is
 * ignored.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface HttpRouteSpec extends RouteFilterSpec<HttpRouteSpec> permits DefaultHttpRouteSpec {

    /**
     * Accept requests with these media types only, like {@code @Consumes} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec consumes(MediaType... mediaTypes);

    /**
     * Accept requests with any media type.
     *
     * @return The route
     */
    HttpRouteSpec consumesAll();

    /**
     * Produce these media types, like {@code @Produces} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec produces(MediaType... mediaTypes);

    /**
     * Give the route the annotations of an annotated element: the features that read the
     * annotations of the matched route, such as security rules, versioning, filter binding, route
     * conditions and the message body writers, see them as if they were on a controller method,
     * and so does the return type of the route.
     *
     * <p>When the element is an {@link ExecutableMethod}, e.g. a method of a resource that a
     * framework integration routes with handler functions, the route implements that bean
     * method: besides its annotations, the target method, declaring type and method name of the
     * route are the ones of the bean method, so the local {@code @Error} methods of the bean class
     * apply to the route, and the route is described as the bean method. The arguments of the
     * route stay those of the handler. Any other element, e.g. a
     * {@link io.micronaut.inject.BeanDefinition}, gives the route its annotations only.</p>
     *
     * <pre>{@code
     * ExecutableMethod<Payments, String> pay = beanContext.getBeanDefinition(Payments.class).getRequiredMethod("pay", long.class);
     * routes.POST("/payments/{amount}", (request, pathVariables) -> HttpResponse.ok(payments.pay(pathVariables.getLong("amount"))))
     *     .annotationMetadata(pay);
     * }</pre>
     *
     * <p>The route keeps the element: an integration finds it back on the matched route with
     * {@link io.micronaut.web.router.MethodBasedRouteInfo#getAnnotationMetadataProvider()}, and
     * the bean method of the route with {@code instanceof ExecutableMethod}. The last element
     * given to the route wins.</p>
     *
     * @param annotationMetadata The annotated element whose annotations the route has, an
     *                           {@link ExecutableMethod} for a route that implements a bean method
     * @return The route
     */
    HttpRouteSpec annotationMetadata(AnnotationMetadataProvider annotationMetadata);

    /**
     * Annotate the route with an annotation, like an annotation on a controller method, with
     * the {@code annotate} methods of the compile-time elements, e.g.
     * {@code io.micronaut.inject.ast.Element#annotate}: the features that read the annotations of
     * the matched route see it, e.g. a {@code @FilterMatcher} annotation binds its filters to the
     * route, and {@code @Version} selects the route by the version of the request. Several
     * annotations are chained calls.
     *
     * <pre>{@code
     * routes.POST("/payments/{amount}", payHandler)
     *     .annotate(Audited.class)
     *     .annotate(Version.class, version -> version.value("2"));
     * }</pre>
     *
     * <p>If the route already has the annotation, the members of the given one are merged with,
     * and override, the existing ones; a repeatable annotation is added to the existing ones. The
     * annotations of the route are layered like the annotations of a controller method over the
     * ones of its class: the annotations of the element given with
     * {@link #annotationMetadata(AnnotationMetadataProvider)}, then of the
     * {@link HttpRouteGroup#annotate(AnnotationValue) groups} of the route, outer group first,
     * then of the route, each overriding the members of the same annotation before it.</p>
     *
     * <p>The meta-annotations of an annotation type are not known at runtime: a feature that
     * looks up an annotation by its own type sees it, like the ones above, {@code @CrossOrigin},
     * {@code @ExecuteOn} or a security annotation, but a feature that looks up a stereotype of
     * the annotation needs the stereotype in the annotation value, see
     * {@link AnnotationValueBuilder#stereotype(AnnotationValue)}. The expressions of an
     * annotation, e.g. of {@code @RouteCondition}, are compiled with the annotated code: a route
     * condition is declared with {@link #where(Predicate)}.</p>
     *
     * @param annotationValue The annotation
     * @param <T>             The annotation type
     * @return The route
     * @since 5.3.0
     */
    <T extends Annotation> HttpRouteSpec annotate(AnnotationValue<T> annotationValue);

    /**
     * Annotate the route, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param consumer       A function that receives the {@link AnnotationValueBuilder}
     * @param <T>            The annotation type
     * @return The route
     * @since 5.3.0
     */
    default <T extends Annotation> HttpRouteSpec annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(consumer, "consumer");
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        return annotate(builder.build());
    }

    /**
     * Annotate the route with an annotation without members, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec annotate(String annotationType) {
        return annotate(annotationType, builder -> { });
    }

    /**
     * Annotate the route, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param consumer       A function that receives the {@link AnnotationValueBuilder}
     * @param <T>            The annotation type
     * @return The route
     * @since 5.3.0
     */
    default <T extends Annotation> HttpRouteSpec annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(consumer, "consumer");
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        return annotate(builder.build());
    }

    /**
     * Annotate the route with an annotation without members, e.g. a {@code @FilterMatcher}
     * annotation, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param <T>            The annotation type
     * @return The route
     * @since 5.3.0
     */
    default <T extends Annotation> HttpRouteSpec annotate(Class<T> annotationType) {
        return annotate(annotationType, builder -> { });
    }

    /**
     * Declare the type of the body of the responses of the route, like the return type
     * {@code HttpResponse<R>} of a controller method: the message body writer is selected for the
     * declared type, with its type arguments and annotations, instead of the runtime class of the
     * body, e.g. a writer or a JSON view for {@code List<Item>} instead of one for
     * {@code ArrayList}. The handler still returns an {@code HttpResponse}, or a stage of one;
     * a body that is not an instance of the declared type is written as its runtime class, like
     * the body of a controller route.
     *
     * <pre>{@code
     * routes.GET("/items", (request, pathVariables) -> HttpResponse.ok(items.findAll()))
     *     .responseType(Argument.listOf(Item.class));
     * }</pre>
     *
     * <p>A response without a body, and a handler that returns no response, are answered like
     * those of a controller route. The route has the declared type for every handler kind,
     * e.g. {@code CompletionStage<HttpResponse<R>>} for a handler that completes the response
     * later, and for the features that read the return type of the matched route, see
     * {@link io.micronaut.web.router.RouteInfo#getResponseBodyType()}.</p>
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec responseType(Argument<?> responseType);

    /**
     * Declare the type of the body of the responses as a class: {@code responseType(Argument.of(responseType))},
     * see {@link #responseType(Argument)}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }

    /**
     * Run the route on the named executor, like {@code @ExecuteOn} on a controller method. It
     * applies whatever the thread selection of the server.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route
     */
    HttpRouteSpec executeOn(String executorName);

    /**
     * Run the route on the event loop, like {@code @NonBlocking} on a controller method, when the
     * server selects threads automatically. The route must not block.
     *
     * @return The route
     */
    HttpRouteSpec nonBlocking();

    /**
     * Route the requests on this port only, like {@code @Controller(port = ...)}: the server opens
     * the port when it starts, and the route does not match a request on another port, which is
     * answered as if the route did not exist. A route without a port matches the requests on the
     * default ports of the server only, once a route of the application has a port. The port of
     * the route overrides the port of its {@link HttpRouteGroup#port(int) group}.
     *
     * <pre>{@code
     * routes.GET("/metrics", (request, pathVariables) -> HttpResponse.ok(metrics.scrape()))
     *     .port(9090);
     * }</pre>
     *
     * <p>A route table built at runtime cannot open a port: its routes cannot have one.</p>
     *
     * @param port The port, between {@code 1} and {@code 65535}: unlike
     *             {@code @Controller(port = ...)}, a negative port or {@code 0}, a random port the
     *             route could not match, is rejected
     * @return The route
     * @throws IllegalArgumentException if the port is not between {@code 1} and {@code 65535}
     * @since 5.3.0
     */
    HttpRouteSpec port(int port);

    /**
     * Match the requests that meet a condition only, like {@code @RouteCondition} on a controller
     * method: a request the condition rejects is answered as if the route did not exist, by
     * another route of the same URI and method, e.g. one with another condition, or by
     * {@code 404}, never by a {@code 405}, {@code 415} or {@code 406} of this route. The
     * conditions of the groups of the route, outer group first, then those of the route, must
     * all be met. The conditions are evaluated while the request is matched, for every request
     * whose path the route matches: they must be fast and must not block, nor read the body.
     *
     * <pre>{@code
     * routes.GET("/reports/{id}", (request, pathVariables) -> HttpResponse.ok(reports.csv(pathVariables.getLong("id"))))
     *     .where(RequestPredicates.header("X-Export", "csv"));
     * routes.GET("/reports/{id}", (request, pathVariables) -> HttpResponse.ok(reports.find(pathVariables.getLong("id"))));
     * }</pre>
     *
     * <p>A request both routes of the example match, with the header, is ambiguous: the most
     * specific route answers it, and the two routes are equally specific, so it is answered with
     * {@code 400}. {@link RequestPredicates} builds conditions on the headers, query parameters,
     * media types and method of the request.</p>
     *
     * @param condition The condition
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec where(Predicate<HttpRequest<?>> condition);

    /**
     * Break a tie with other routes that are equally good for a request: the route with the
     * lowest order answers it. The router selects the most specific route by its URI template,
     * then by the media types, then prefers an explicit {@code HEAD} route over an implicit one;
     * only the routes still left after that are compared by their order. So the order never
     * makes a less specific route win over a more specific one: it chooses among routes of the
     * same URI template whose {@link #where(Predicate) conditions} a request both meets, e.g. a
     * specialized route and a fallback. Two routes left with the same order still make the
     * request ambiguous, answered with {@code 400}.
     *
     * <pre>{@code
     * routes.GET("/reports/{id}", csvHandler)
     *     .where(RequestPredicates.queryParam("format", "csv"))
     *     .order(-1);
     * routes.GET("/reports/{id}", reportHandler); // the order 0: answers the other requests
     * }</pre>
     *
     * <p>The default order is {@code 0}, the order of a controller route, or the order of the
     * {@link HttpRouteGroup#order(int) group} of the route, which the order of the route
     * overrides. The order of an {@link HttpRoutes} bean orders the beans, not their routes.</p>
     *
     * @param order The order, lower wins
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec order(int order);

    /**
     * Give the route an attribute: metadata of the route for the code that handles its requests,
     * which reads it from the matched route with {@link io.micronaut.web.router.RouteInfo#getAttribute(String)},
     * e.g. a route filter, a server filter or the handler. The attributes of the
     * {@link HttpRouteGroup#attribute(String, Object) groups} of the route apply too, and an
     * attribute of the route overrides the attribute of a group with the same name.
     *
     * <pre>{@code
     * routes.path("/admin", admin -> {
     *     admin.attribute("role", "admin");
     *     admin.GET("/users", usersHandler);
     *     admin.GET("/audit", auditHandler).attribute("role", "auditor");
     * });
     * routes.filter("/admin/**").before(request -> {
     *     String role = RouteAttributes.getRouteInfo(request)
     *         .flatMap(route -> route.getAttribute("role", String.class))
     *         .orElseThrow();
     *     return hasRole(request, role) ? null : HttpResponse.forbidden();
     * });
     * }</pre>
     *
     * <p>They are the attributes of the route, not of the request, see
     * {@link io.micronaut.web.router.RouteAttributes} for the route of a request.</p>
     *
     * @param name  The name of the attribute
     * @param value The value of the attribute
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec attribute(String name, Object value);
}
