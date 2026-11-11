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
 * The {@link FilterSpec}: the registration of the filter holds its executor until the routes
 * that have the filter are built.
 *
 * @param owner        The route, the group or the server filter that declares the filter
 * @param registration The filter
 * @param <S>          The type of the owner
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultFilterSpec<S>(S owner, FilterRegistration registration) implements FilterSpec<S> {

    @Override
    public FilterSpec<S> executeOn(String executorName) {
        registration.executeOn(executorName);
        return this;
    }

    @Override
    public FilterSpec<S> nonBlocking() {
        registration.nonBlocking();
        return this;
    }

    @Override
    public S and() {
        return owner;
    }
}
