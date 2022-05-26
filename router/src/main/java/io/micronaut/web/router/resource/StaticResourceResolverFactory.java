/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.web.router.resource;

import java.util.List;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

/**
 * A factory for creating the default {@link io.micronaut.web.router.resource.StaticResourceResolver}.
 *
 * @author graemerocher
 * @since 3.1.0
 * @see io.micronaut.web.router.resource.StaticResourceResolver
 */
@Factory
public class StaticResourceResolverFactory {

    /**
     * Builds the {@link io.micronaut.web.router.resource.StaticResourceResolver} instance.
     * @param configurations The configurations
     * @return The {@link io.micronaut.web.router.resource.StaticResourceResolver}
     */
    @Singleton
    @NonNull
    protected StaticResourceResolver build(List<StaticResourceConfiguration> configurations) {
        if (configurations.isEmpty()) {
            return StaticResourceResolver.EMPTY;
        } else {
            return new StaticResourceResolver(configurations);
        }
    }
}
