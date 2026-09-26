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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.inject.ExecutableMethod;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The settings a handler route, see {@link HttpRouteSpec}, and a group of routes, see
 * {@link HttpRouteGroup}, share: the media types, the annotations, the attributes, the conditions,
 * the order and the port, besides the filters of {@link RouteFilterSpec} and the executor of
 * {@link ExecutionSpec}.
 *
 * <p>On a route, a setting is the value of the route. On a group, it is the default of the
 * routes declared in the lambda of the group, and of its nested groups, wherever it is declared
 * in the lambda, before the routes, after them or in between: a route, or a nested group, with
 * its own value overrides the one of the group, so the innermost value wins. The conditions,
 * the attributes and the annotations of the groups and of the route are combined, see each
 * method.</p>
 *
 * <pre>{@code
 * routes.path("/admin", admin -> admin
 *     .executeOn(TaskExecutors.BLOCKING)
 *     .annotate(Audited.class)
 *     .GET("/users", (request, pathVariables) -> HttpResponse.ok(users.findAll())));
 * }</pre>
 *
 * @param <S> The type of the route or the group
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface RouteSpec<S extends RouteSpec<S>> extends RouteFilterSpec<S>, ExecutionSpec<S> permits HttpRouteSpec, HttpRouteGroup {

    /**
     * Accept requests with these media types only, like {@code @Consumes} on a controller method,
     * or on a controller for a group.
     *
     * <p>On a group, the routes of the group, and of its nested groups, consume them instead of
     * {@code application/json}. A route that declares what it consumes, with this method or
     * {@link #consumesAll()}, and a nested group that does, replace them, like a method-level
     * {@code @Consumes} replaces the one of its controller. So does a form route, e.g.
     * {@link HttpRouteBuilder#handleForm(HttpMethod, String, FormRequestHandler)}, which consumes
     * the form media types: a form handler reads a form.</p>
     *
     * <pre>{@code
     * routes.path("/notes", notes -> {
     *     notes.consumes(MediaType.TEXT_PLAIN_TYPE).produces(MediaType.TEXT_PLAIN_TYPE);
     *     notes.POST("/", String.class, (request, pathVariables, text) -> HttpResponse.ok(notes.save(text)));
     *     notes.POST("/json", Note.class, (request, pathVariables, note) -> HttpResponse.ok(notes.save(note)))
     *         .consumes(MediaType.APPLICATION_JSON_TYPE);
     * });
     * }</pre>
     *
     * <p>The media types, and the executor, see {@link ExecutionSpec#executeOn(String)}, of a
     * group apply to its routes to handlers, including the implicit {@code HEAD} routes, the
     * routes declared for several methods and the declared routes, not to its locator routes,
     * whose located routes declare their own, nor to its error and status routes.</p>
     *
     * @param mediaTypes The media types
     * @return The route or the group
     */
    S consumes(MediaType... mediaTypes);

    /**
     * Accept requests with any media type. On a group, the default of its routes, like
     * {@link #consumes(MediaType...)}.
     *
     * @return The route or the group
     */
    S consumesAll();

    /**
     * Produce these media types, like {@code @Produces} on a controller method, or on a
     * controller for a group. On a group, the routes of the group, and of its nested groups,
     * produce them; a route that declares what it produces, or a nested group that does,
     * replaces them.
     *
     * @param mediaTypes The media types
     * @return The route or the group
     */
    S produces(MediaType... mediaTypes);

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
     * <p>On a group, the element is the default element of the routes of the group, and of its
     * nested groups, to handlers: a route without its own element has the one of its innermost
     * group that has one, as if it were given to the route, e.g. the
     * {@link io.micronaut.inject.BeanDefinition} of a resource for every route of the resource.
     * The element of a route, or of a nested group, replaces it. The annotations given with
     * {@link #annotate(AnnotationValue)} to the groups and to the route override the ones of the
     * element.</p>
     *
     * @param annotationMetadata The annotated element whose annotations the route has, an
     *                           {@link ExecutableMethod} for a route that implements a bean method
     * @return The route or the group
     */
    S annotationMetadata(AnnotationMetadataProvider annotationMetadata);

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
     * {@link #annotationMetadata(AnnotationMetadataProvider)}, then of the groups of the route,
     * outer group first, then of the route, each overriding the members of the same annotation
     * before it.</p>
     *
     * <p>On a group, the routes of the group, and of its nested groups, have the annotation; the
     * annotation of a nested group or of a route overrides the members it sets.</p>
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
     * @return The route or the group
     */
    <T extends Annotation> S annotate(AnnotationValue<T> annotationValue);

    /**
     * Annotate the route, or the routes of the group, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param consumer       A function that receives the {@link AnnotationValueBuilder}
     * @param <T>            The annotation type
     * @return The route or the group
     */
    default <T extends Annotation> S annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(consumer, "consumer");
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        return annotate(builder.build());
    }

    /**
     * Annotate the route, or the routes of the group, with an annotation without members, see
     * {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @return The route or the group
     */
    default S annotate(String annotationType) {
        return annotate(annotationType, builder -> { });
    }

    /**
     * Annotate the route, or the routes of the group, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param consumer       A function that receives the {@link AnnotationValueBuilder}
     * @param <T>            The annotation type
     * @return The route or the group
     */
    default <T extends Annotation> S annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        Objects.requireNonNull(annotationType, "annotationType");
        Objects.requireNonNull(consumer, "consumer");
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        consumer.accept(builder);
        return annotate(builder.build());
    }

    /**
     * Annotate the route, or the routes of the group, with an annotation without members, e.g. a
     * {@code @FilterMatcher} annotation, see {@link #annotate(AnnotationValue)}.
     *
     * @param annotationType The annotation type
     * @param <T>            The annotation type
     * @return The route or the group
     */
    default <T extends Annotation> S annotate(Class<T> annotationType) {
        return annotate(annotationType, builder -> { });
    }

    /**
     * Give the route an attribute: metadata of the route for the code that handles its requests,
     * which reads it from the matched route with {@link io.micronaut.web.router.RouteInfo#getAttribute(String)},
     * e.g. a route filter, a server filter or the handler. They are the attributes of the route,
     * not of the request, see {@link io.micronaut.web.router.RouteAttributes} for the route of a
     * request.
     *
     * <p>On a group, the routes of the group, and of its nested groups, have the attribute; the
     * attribute of a nested group or of a route with the same name overrides it.</p>
     *
     * <pre>{@code
     * routes.path("/admin", admin -> {
     *     admin.attribute("role", "admin");
     *     admin.GET("/users", usersHandler);
     *     admin.GET("/audit", auditHandler).attribute("role", "auditor");
     * });
     * routes.filter("/admin/**").beforeReplacing(request -> {
     *     String role = RouteAttributes.getRouteInfo(request)
     *         .flatMap(route -> route.getAttribute("role", String.class))
     *         .orElseThrow();
     *     return hasRole(request, role) ? null : HttpResponse.forbidden();
     * });
     * }</pre>
     *
     * @param name  The name of the attribute
     * @param value The value of the attribute
     * @return The route or the group
     */
    S attribute(String name, Object value);

    /**
     * Match the requests that meet a condition only, like {@code @RouteCondition} on a controller
     * method: a request the condition rejects is answered as if the route did not exist, by
     * another route of the same URI and method, e.g. one with another condition, or by
     * {@code 404}, never by a {@code 405}, {@code 415} or {@code 406} of this route. The
     * conditions are evaluated while the request is matched, for every request whose path the
     * route matches: they must be fast and must not block, nor read the body.
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
     * <p>On a group, the condition applies to the routes of the group, including its locator
     * routes and the routes of its nested groups: a route matches a request that meets the
     * conditions of its groups, outer group first, and its own, which must all be met.</p>
     *
     * @param condition The condition
     * @return The route or the group
     */
    S where(Predicate<HttpRequest<?>> condition);

    /**
     * Constrain the path variables of the route: the route matches a request only when the
     * constraint accepts the path variables its URI template bound. Otherwise the route is not a
     * match, as if its URI template did not match: the request goes on to the other routes, e.g. a
     * route with the same URI template, or is answered with {@code 404} when none matches.
     *
     * <pre>{@code
     * routes.path("/shops/{shop}", shop -> shop
     *     .constrain("shop", SHOPS)
     *     .route(locator));
     * routes.GET("/orders/{id}", ordersHandler)
     *     .constrain("id", Long.class, id -> id > 0);
     * }</pre>
     *
     * <p>A constraint runs while the request is matched, after the URI template of the route
     * matched and bound the variables, before the media types, the conditions, see
     * {@link #where(Predicate)}, and the ambiguity between the routes are considered. It is given
     * the same {@link PathVariables} the handler is given. Since a rejected route is not a match,
     * the methods allowed on a path for a {@code 405}, the media types for a {@code 415} or
     * {@code 406}, a CORS preflight request and the implicit {@code HEAD} route consider only
     * the routes whose constraints pass: a rejected value never produces a {@code 405}. A
     * constraint that throws an exception, e.g. a variable that does not convert, rejects the
     * variables; the exception is logged at debug level.</p>
     *
     * <p>A constraint should be cheap, and must not have side effects: it runs for every request
     * the URI template of the route matches, including the requests another route answers. A
     * route without constraints costs nothing more to match.</p>
     *
     * <p>On a group, the constraint applies to the routes of the group, including the routes of
     * its nested groups: the constraints of the groups, outer group first, and of the route must
     * all pass.</p>
     *
     * @param accepted Whether the path variables are accepted
     * @return The route or the group
     * @since 5.3.0
     */
    @Experimental
    S constrain(Predicate<? super PathVariables> accepted);

    /**
     * Constrain a path variable, see {@link #constrain(Predicate)}: a request whose variable has
     * no value, or a value the predicate does not accept, is not a match of the route.
     *
     * <pre>{@code
     * routes.GET("/files/{name}", filesHandler)
     *     .constrain("name", name -> !name.startsWith("."));
     * }</pre>
     *
     * @param variable The name of the variable
     * @param accepted Whether the value, as a string, is accepted
     * @return The route or the group
     * @since 5.3.0
     */
    @Experimental
    default S constrain(String variable, Predicate<? super String> accepted) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(accepted, "accepted");
        return constrain(variables -> {
            String value = variables.findString(variable).orElse(null);
            return value != null && accepted.test(value);
        });
    }

    /**
     * Constrain a path variable converted to a type, see {@link #constrain(Predicate)}: a request
     * whose variable has no value, a value that does not convert to the type, or a value the
     * predicate does not accept, is not a match of the route.
     *
     * <pre>{@code
     * routes.GET("/orders/{id}", ordersHandler)
     *     .constrain("id", Long.class, id -> id > 0); // "/orders/abc" is not a match either
     * }</pre>
     *
     * @param variable The name of the variable
     * @param type     The type to convert the value to
     * @param accepted Whether the converted value is accepted
     * @param <T>      The type
     * @return The route or the group
     * @since 5.3.0
     */
    @Experimental
    default <T> S constrain(String variable, Class<T> type, Predicate<? super T> accepted) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accepted, "accepted");
        // a conversion error throws, which rejects the variables
        return constrain(variables -> variables.find(variable, type).map(accepted::test).orElse(false));
    }

    /**
     * Constrain a path variable to a set of values, see {@link #constrain(Predicate)}: a request
     * whose variable has another value, or none, is not a match of the route. The values are
     * copied when the constraint is declared.
     *
     * <pre>{@code
     * routes.path("/shops/{shop}", shop -> shop
     *     .constrain("shop", Set.of("north", "south"))
     *     .GET("/stock", stockHandler));
     * }</pre>
     *
     * @param variable The name of the variable
     * @param values   The accepted values
     * @return The route or the group
     * @since 5.3.0
     */
    @Experimental
    default S constrain(String variable, Collection<String> values) {
        Set<String> accepted = Set.copyOf(values);
        return constrain(variable, accepted::contains);
    }

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
     * <p>The order is global: it is compared between any routes left for a request, controller
     * routes and the routes of every {@link HttpRoutes} bean and group alike, and the lower order
     * wins. The default order is {@code 0}, the order of a controller route, so a route with a
     * negative order wins a tie with a controller route and one with a positive order loses it.
     * On a group, the order is the default of the routes of the group, and of its nested groups,
     * unless they or a nested group have their own. The order of an {@link HttpRoutes} bean orders
     * the beans, not their routes.</p>
     *
     * @param order The order, lower wins
     * @return The route or the group
     */
    S order(int order);

    /**
     * Route the requests on this port only, like {@code @Controller(port = ...)}: the server opens
     * the port when it starts, and the route does not match a request on another port, which is
     * answered as if the route did not exist. A route without a port matches the requests on the
     * default ports of the server only, once a route of the application has a port.
     *
     * <pre>{@code
     * routes.GET("/metrics", (request, pathVariables) -> HttpResponse.ok(metrics.scrape()))
     *     .port(9090);
     * }</pre>
     *
     * <p>On a group, the routes of the group, including its locator routes and the routes of its
     * nested groups, inherit the port; a nested group or a route with its own port overrides it.
     * Located routes cannot open a port: their routes cannot have one.</p>
     *
     * @param port The port, between {@code 1} and {@code 65535}: unlike
     *             {@code @Controller(port = ...)}, a negative port or {@code 0}, a random port the
     *             route could not match, is rejected
     * @return The route or the group
     * @throws IllegalArgumentException if the port is not between {@code 1} and {@code 65535}
     */
    S port(int port);

    /**
     * Route the requests on the port of a property, like {@code @Controller(port = "${my.admin.port}")},
     * whose {@code port} member is a string: a number, or an expression with property
     * placeholders, with defaults, e.g. {@code ${my.admin.port:8081}}, resolved with the
     * environment of the application like the port of a controller, when the routes are declared.
     * Otherwise the same as {@link #port(int)}, on a route and on a group.
     *
     * <pre>{@code
     * routes.GET("/metrics", metricsHandler).port("${management.port:9090}");
     * }</pre>
     *
     * @param port The port, or an expression that resolves to it
     * @return The route or the group
     * @throws io.micronaut.context.exceptions.ConfigurationException if a placeholder cannot be resolved
     * @throws IllegalArgumentException if the port is not a number between {@code 1} and {@code 65535}
     */
    S port(String port);
}
