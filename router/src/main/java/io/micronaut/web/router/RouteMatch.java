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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.RequestBinderRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * A {@link Route} that is executable.
 *
 * @param <R> The route
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteMatch<R> extends Callable<R>, AnnotationMetadataProvider {

    /**
     * @return The route info
     */
    RouteInfo<R> getRouteInfo();

    /**
     * @return The variable values following a successful match.
     */
    Map<String, Object> getVariableValues();

    /**
     * Fulfill argument values.
     *
     * @param argumentValues The argument values
     * @deprecated No longer used
     */
    @Deprecated(forRemoval = true, since = "4.9")
    void fulfill(Map<String, Object> argumentValues);

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param requestBinderRegistry The request binder registry
     * @param request               The request
     * @since 4.0.0
     */
    void fulfillBeforeFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request);

    /**
     * Attempt to satisfy the arguments of the given route with the data from the given request.
     *
     * @param requestBinderRegistry The request binder registry
     * @param request               The request
     * @since 4.0.0
     */
    void fulfillAfterFilters(RequestBinderRegistry requestBinderRegistry, HttpRequest<?> request);

    /**
     * @return Whether the route match can be executed without passing any additional arguments i.e. via
     * {@link #execute()}
     * @since 4.0.0
     */
    boolean isFulfilled();

    /**
     * Return whether the given named input is required by this route.
     *
     * @param name The name of the input
     * @return True if it is
     */
    Optional<Argument<?>> getRequiredInput(String name);

    /**
     * <p>Returns the required arguments for this RouteMatch.</p>
     *
     * @return The required arguments in order to invoke this route
     */
    default Collection<Argument<?>> getRequiredArguments() {
        return Collections.emptyList();
    }

    /**
     * Execute the route with the given values. Note if there are required arguments returned from
     * {@link #getRequiredArguments()} this method will throw an {@link IllegalArgumentException}.
     *
     * @return The result
     */
    R execute();

    /**
     * Same as {@link #execute()}.
     *
     * @return The result
     * @throws Exception When an exception occurs
     */
    @Override
    default R call() throws Exception {
        return execute();
    }

    /**
     * Is the given input satisfied.
     *
     * @param name The name of the input
     * @return True if it is
     */
    boolean isSatisfied(String name);

}
