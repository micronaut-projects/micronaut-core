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

/**
 * The {@link LocatedBodyFilterSpec}: the registration of the filter, and the located route to go back to.
 *
 * @param owner        The located route
 * @param registration The filter
 * @param <T> The type of the located target
 * @param <B> The type of the body
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultLocatedBodyFilterSpec<T, B>(LocatedHttpBodyRouteSpec<T, B> owner, FilterRegistration registration) implements LocatedBodyFilterSpec<T, B> {

    @Override
    public LocatedBodyFilterSpec<T, B> executeOn(String executorName) {
        registration.executeOn(executorName);
        return this;
    }

    @Override
    public LocatedBodyFilterSpec<T, B> nonBlocking() {
        registration.nonBlocking();
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> and() {
        return owner;
    }
}
