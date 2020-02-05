/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link Route} that is executable.
 *
 * @param <R> The route
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteMatch<R> extends Callable<R>, Predicate<HttpRequest>, AnnotationMetadataProvider {

    /**
     * The declaring type of the route.
     *
     * @return The declaring type
     */
    Class<?> getDeclaringType();

    /**
     * @return The variable values following a successful match.
     */
    Map<String, Object> getVariableValues();

    /**
     * Execute the route with the given values. The passed map should contain values for every argument returned by
     * {@link #getRequiredArguments()}.
     *
     * @param argumentValues The argument values
     * @return The result
     */
    R execute(Map<String, Object> argumentValues);

    /**
     * Returns a new {@link RouteMatch} fulfilling arguments required by this route to execute. The new route will not
     * return the given arguments from the {@link #getRequiredArguments()} method.
     *
     * @param argumentValues The argument values
     * @return The fulfilled route
     */
    RouteMatch<R> fulfill(Map<String, Object> argumentValues);

    /**
     * Decorates the execution of the route with the given executor.
     *
     * @param executor The executor
     * @return A new route match
     */
    RouteMatch<R> decorate(Function<RouteMatch<R>, R> executor);

    /**
     * Return whether the given named input is required by this route.
     *
     * @param name The name of the input
     * @return True if it is
     */
    Optional<Argument<?>> getRequiredInput(String name);

    /**
     * @return The argument that represents the body
     */
    Optional<Argument<?>> getBodyArgument();

    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    List<MediaType> getProduces();

    /**
     * <p>Returns the required arguments for this RouteMatch.</p>
     *
     * @return The required arguments in order to invoke this route
     */
    default Collection<Argument> getRequiredArguments() {
        return Collections.emptyList();
    }

    /**
     * @return The return type
     */
    ReturnType<? extends R> getReturnType();

    /**
     * Execute the route with the given values. Note if there are required arguments returned from
     * {@link #getRequiredArguments()} this method will throw an {@link IllegalArgumentException}.
     *
     * @return The result
     */
    default R execute() {
        return execute(Collections.emptyMap());
    }

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
     * @return Whether the route match can be executed without passing any additional arguments ie. via
     * {@link #execute()}
     */
    default boolean isExecutable() {
        return getRequiredArguments().size() == 0;
    }

    /**
     * Return whether the given named input is required by this route.
     *
     * @param name The name of the input
     * @return True if it is
     */
    default boolean isRequiredInput(String name) {
        return getRequiredInput(name).isPresent();
    }

    /**
     * Whether the specified content type is an accepted type.
     *
     * @param contentType The content type
     * @return True if it is
     */
    boolean accept(@Nullable MediaType contentType);

    /**
     * Whether the specified content type is explicitly an accepted type.
     *
     * @param contentType The content type
     * @return True if it is
     */
    default boolean explicitAccept(@Nullable MediaType contentType) {
        return false;
    }

    /**
     * Is the given input satisfied.
     *
     * @param name The name of the input
     * @return True if it is
     */
    default boolean isSatisfied(String name) {
        Object val = getVariableValues().get(name);
        return val != null && !(val instanceof UnresolvedArgument);
    }
}
