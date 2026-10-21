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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.uri.RouteTemplateParser;

/**
 * A group of the routes of located targets of a type, declared with
 * {@link LocatedHttpRouteScope#group} or, under a prefix, with {@link LocatedHttpRouteScope#path}:
 * a {@link HttpRouteGroup} of a {@link LocatedHttpRouteBuilder}, whose routes and locators
 * receive the located target, like the ones of the builder. Its filters, settings, error routes
 * and status routes apply to the routes of the group, as for an {@link HttpRouteGroup}.
 *
 * <pre>{@code
 * public void routes(LocatedHttpRouteBuilder<Order> order) {
 *     order.path("/items", items -> {
 *         items.error(NoSuchElementException.class, (request, error) -> HttpResponse.notFound());
 *         items.GET("/{item}").handle((request, pathVariables, current) ->
 *             HttpResponse.ok(current.item(pathVariables.getInt("item"))));
 *         items.locate("/{item}/parts", (request, pathVariables, current) -> current.item(pathVariables.getInt("item")), partRoutes);
 *     });
 * }
 * }</pre>
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpRouteGroup<T> extends LocatedHttpRouteScope<T>, RouteSpec<LocatedHttpRouteGroup<T>> permits DefaultLocatedHttpRouteGroup {

    /**
     * Parse the {@code String} templates of the routes and of the locator prefixes this group,
     * and its nested groups, declare with a parser of another template language than the URI
     * templates of Micronaut, e.g. the templates of a JAX-RS application. A template is parsed once,
     * when its route is added by its terminal, or when its locator is declared:
     *
     * <pre>{@code
     * routes.group(jaxrs -> {
     *     jaxrs.templates(application.templates()).selectedBy(application.selector());
     *     jaxrs.GET("/items/{id: [0-9]+}", itemHandler);
     * });
     * }</pre>
     *
     * <p>Declare it before the routes: the routes declared before it keep the parser they were
     * declared with. A group with a prefix with variables, see
     * {@link LocatedHttpRouteScope#path(String, java.util.function.Consumer)}, accepts URI templates only; the templates of
     * a group with a literal prefix are mounted under it.</p>
     *
     * @param parser The parser of the templates, e.g. {@link io.micronaut.http.uri.UriRouteTemplate#PARSER}
     * @return This group
     * @since 5.3.0
     */
    LocatedHttpRouteGroup<T> templates(RouteTemplateParser parser);
}
