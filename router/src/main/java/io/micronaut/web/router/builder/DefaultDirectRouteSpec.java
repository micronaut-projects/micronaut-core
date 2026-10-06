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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.direct.DirectContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The {@link DirectRouteSpec} of a pending direct route: records the settings of the route, and
 * its terminal declares a {@link DirectRouteDeclaration} per method with them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultDirectRouteSpec implements DirectRouteSpec {

    private final DefaultDirectRouteBuilder builder;
    private final List<HttpMethod> methods;
    private final String uriTemplate;
    private final String description;
    private final List<RouteCondition> conditions = new ArrayList<>(0);
    private final List<Predicate<? super PathVariables>> constraints = new ArrayList<>(0);
    private int order;
    private @Nullable String executorName;
    private boolean ended;

    /**
     * @param builder     The builder that declared the route
     * @param methods     The methods of the route
     * @param uriTemplate The URI template, with the prefix and the context path
     * @param description Describes the route for the messages
     */
    DefaultDirectRouteSpec(DefaultDirectRouteBuilder builder, List<HttpMethod> methods, String uriTemplate, String description) {
        this.builder = builder;
        this.methods = methods;
        this.uriTemplate = uriTemplate;
        this.description = description;
    }

    /**
     * @return The description of the route, e.g. {@code GET /health declared by HealthRoutes}
     */
    String description() {
        return description;
    }

    @Override
    public DirectRouteSpec where(RouteCondition condition) {
        Objects.requireNonNull(condition, "condition");
        checkPending();
        // rejected now, and again with the conditions of the prefixes when the routes are built
        DirectConditions.check(condition, this);
        conditions.add(condition);
        return this;
    }

    @Override
    public DirectRouteSpec constrain(Predicate<? super PathVariables> accepted) {
        Objects.requireNonNull(accepted, "accepted");
        checkPending();
        constraints.add(accepted);
        return this;
    }

    @Override
    public DirectRouteSpec executeOn(String executorName) {
        Objects.requireNonNull(executorName, "executorName");
        checkPending();
        this.executorName = executorName;
        return this;
    }

    @Override
    public DirectRouteSpec nonBlocking() {
        checkPending();
        this.executorName = null;
        return this;
    }

    @Override
    public DirectRouteSpec order(int order) {
        checkPending();
        this.order = order;
        return this;
    }

    @Override
    public void respond(HttpResponse<?> response) {
        ResponseTemplate constant = ResponseTemplate.direct(terminal(response));
        end(constant, null, null);
    }

    @Override
    public void respond(Function<? super DirectContext, ? extends @Nullable HttpResponse<?>> response) {
        Function<? super DirectContext, ? extends @Nullable HttpResponse<?>> checked = terminal(response);
        end(null, checked::apply, null);
    }

    @Override
    public void respondAsync(Function<? super DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> response) {
        Function<? super DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> checked = terminal(response);
        end(null, null, checked::apply);
    }

    /**
     * Check the response a terminal was given: without one, the route is dropped and the
     * terminal fails, instead of the startup failing again for a route with no terminal.
     */
    private <T> T terminal(@Nullable T response) {
        checkPending();
        if (response == null) {
            ended = true;
            builder.dropPending(this);
            throw new NullPointerException("response");
        }
        return response;
    }

    private void end(@Nullable ResponseTemplate constant,
                     @Nullable Function<DirectContext, ? extends @Nullable HttpResponse<?>> response,
                     @Nullable Function<DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> asyncResponse) {
        ended = true;
        List<DirectRouteDeclaration> routes = new ArrayList<>(methods.size());
        for (HttpMethod method : methods) {
            DirectRouteDeclaration route = new DirectRouteDeclaration(method.name(), uriTemplate, constant, response, asyncResponse);
            route.conditions.addAll(conditions);
            route.constraints.addAll(constraints);
            route.order = order;
            route.executorName = executorName;
            routes.add(route);
        }
        builder.addRoutes(this, routes);
    }

    private void checkPending() {
        if (ended) {
            throw new IllegalStateException("The direct route " + description
                + " was already ended: give its settings before its one terminal, respond or respondAsync");
        }
    }

    @Override
    public String toString() {
        return "direct route " + description;
    }
}
