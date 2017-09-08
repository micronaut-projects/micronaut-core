/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router;

import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.inject.Argument;
import org.particleframework.inject.MethodExecutionHandle;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A {@link Route} that is executable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RouteMatch<R> extends MethodExecutionHandle<R>, UriMatchInfo, Callable<R>, Predicate<HttpRequest> {

    /**
     * <p>Returns the required arguments for this RouteMatch</p>
     *
     * <p>Note that this is not the save as {@link #getArguments()} as it will include a subset of the arguments excluding those that have been subtracted from the URI variables</p>
     *
     * @return The required arguments in order to invoke this route
     */
    default Collection<Argument> getRequiredArguments() {
        Map<String, Object> matchVariables = getVariables();
        return Arrays.stream(getArguments())
                .filter((arg) -> !matchVariables.containsKey(arg.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Execute the route with the given values. The passed map should contain values for every argument returned by {@link #getRequiredArguments()}
     *
     * @param argumentValues The argument values
     * @return The result
     */
    R execute(Map<String, Object> argumentValues);

    /**
     * Returns a new {@link RouteMatch} fulfilling arguments required by this route to execute. The new route will not
     * return the given arguments from the {@link #getRequiredArguments()} method
     *
     * @param argumentValues The argument values
     * @return The fulfilled route
     */
    RouteMatch<R> fulfill(Map<String, Object> argumentValues);

    /**
     * Decorates the execution of the route with the given executor
     *
     * @param executor The executor
     * @return A new route match
     */
    RouteMatch<R> decorate(Function<RouteMatch<R>, R> executor);

    /**
     * Execute the route with the given values. Note if there are required arguments returned from {@link #getRequiredArguments()} this method will throw an {@link IllegalArgumentException}
     *
     * @return The result
     */
    default R execute() {
        return execute(Collections.emptyMap());
    }

    @Override
    default R call() throws Exception {
        return execute();
    }


    /**
     * @return The matched HTTP method
     */
    HttpMethod getHttpMethod();
}
