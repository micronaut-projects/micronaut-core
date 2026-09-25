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

/**
 * A filter just added to a route, a group or a server filter, see {@link RouteFilterSpec}: choose
 * where it runs, see {@link ExecutionSpec}, then go back to the route or the group with
 * {@link #and()} to continue the declaration.
 *
 * <pre>{@code
 * routes.GET("/reports/{id}", reportHandler)
 *     .before(request -> audit.record(request)).executeOn(TaskExecutors.BLOCKING)
 *     .and()
 *     .produces(MediaType.TEXT_PLAIN_TYPE);
 * }</pre>
 *
 * <p>A filter runs on the thread of the filter chain unless it is given an executor. The executor
 * of a filter is chosen while the routes are declared: once the router took the routes, a change
 * is ignored, like a change of the settings of a route, and once the lambda of the group that
 * declares the filter returned, a change fails, like a change of the settings of the group.</p>
 *
 * @param <S> The route, the group or the server filter that declares the filter
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface FilterSpec<S> extends ExecutionSpec<FilterSpec<S>> permits DefaultFilterSpec {

    /**
     * @return The route, the group or the server filter that declares the filter, to continue its declaration
     */
    S and();
}
