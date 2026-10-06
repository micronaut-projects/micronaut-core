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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A direct route while it is declared, see {@link DefaultDirectRouteBuilder}: its settings are
 * recorded until the routes of every {@link HttpDirectRoutes} bean
 * are declared, when the route is built into a {@link DirectRouteTable}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DirectRouteDeclaration {

    final String httpMethodName;
    final String uriTemplate;
    /**
     * The response given as a value, copied for each request, or {@code null} if a function creates it.
     */
    final @Nullable ResponseTemplate constant;
    /**
     * Creates the response of a request from its path variables, or {@code null}.
     */
    final @Nullable Function<DirectContext, ? extends @Nullable HttpResponse<?>> response;
    /**
     * Creates the stage of the response of a request, or {@code null}.
     */
    final @Nullable Function<DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> asyncResponse;
    final List<RouteCondition> conditions = new ArrayList<>(0);
    final List<Predicate<? super PathVariables>> constraints = new ArrayList<>(0);
    int order;
    /**
     * The name of the executor the function runs on, or {@code null} for the thread that received the request.
     */
    @Nullable String executorName;

    /**
     * @param httpMethodName The name of the method
     * @param uriTemplate    The URI template, with the prefix and the context path
     * @param constant       The response given as a value, or {@code null}
     * @param response       Creates the response of a request, or {@code null}
     * @param asyncResponse  Creates the stage of the response of a request, or {@code null}
     */
    DirectRouteDeclaration(String httpMethodName,
                           String uriTemplate,
                           @Nullable ResponseTemplate constant,
                           @Nullable Function<DirectContext, ? extends @Nullable HttpResponse<?>> response,
                           @Nullable Function<DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> asyncResponse) {
        this.httpMethodName = Objects.requireNonNull(httpMethodName, "httpMethodName");
        this.uriTemplate = Objects.requireNonNull(uriTemplate, "uriTemplate");
        this.constant = constant;
        this.response = response;
        this.asyncResponse = asyncResponse;
    }

    /**
     * @return Whether the route is asynchronous: it runs on an executor, or completes later
     */
    boolean isAsync() {
        return asyncResponse != null || executorName != null;
    }

    @Override
    public String toString() {
        return "direct route " + httpMethodName + ' ' + uriTemplate;
    }
}
