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

import io.micronaut.core.type.Argument;
import io.micronaut.inject.MethodExecutionHandle;

import java.util.Arrays;
import java.util.Collection;

/**
 * Match a route based on a method.
 *
 * @param <T> The target
 * @param <R> The route
 */
public interface MethodBasedRouteMatch<T, R> extends RouteMatch<R>, MethodExecutionHandle<T, R> {

    /**
     * <p>Returns the required arguments for this RouteMatch<./p>
     * <p>
     * <p>Note that this is not the save as {@link #getArguments()} as it will include a subset of the arguments
     * excluding those that have been subtracted from the URI variables.</p>
     *
     * @return The required arguments in order to invoke this route
     */
    default Collection<Argument> getRequiredArguments() {
        return Arrays.asList(getArguments());
    }
}
