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
 * A filter just added to a located route, see {@link FilterSpec}: {@link #and()} goes back to the
 * located route.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedFilterSpec<T> extends FilterSpec<HttpRouteSpec> permits DefaultLocatedFilterSpec {

    @Override
    LocatedFilterSpec<T> executeOn(String executorName);

    @Override
    LocatedFilterSpec<T> nonBlocking();

    @Override
    LocatedHttpRouteSpec<T> and();
}
