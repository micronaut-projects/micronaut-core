/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.web.router.RouteMatch;
import jakarta.inject.Singleton;

/**
 * A class containing methods to aid in satisfying arguments of a {@link io.micronaut.web.router.Route}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Internal
public class RequestArgumentSatisfier {

    private final RequestBinderRegistry binderRegistry;

    /**
     * @param requestBinderRegistry The Request binder registry
     */
    public RequestArgumentSatisfier(RequestBinderRegistry requestBinderRegistry) {
        this.binderRegistry = requestBinderRegistry;
    }

    /**
     * @return The request binder registry
     */
    public RequestBinderRegistry getBinderRegistry() {
        return binderRegistry;
    }

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param route   The route
     * @param request The request
     */
    public void fulfillArgumentRequirementsBeforeFilters(RouteMatch<?> route, HttpRequest<?> request) {
        route.fulfillBeforeFilters(binderRegistry, request);
    }

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param route   The route
     * @param request The request
     */
    public void fulfillArgumentRequirementsAfterFilters(RouteMatch<?> route, HttpRequest<?> request) {
        route.fulfillAfterFilters(binderRegistry, request);
    }

}
