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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;

/**
 * The URI routes of the controllers of a compilation, generated at compile time for
 * {@link io.micronaut.web.router.annotation.PrecompiledHttpRoutes} and loaded as a service.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface PrecompiledHttpRoutesDefinition {

    /**
     * @return The names of the controller types whose routes are precompiled
     */
    String[] controllerTypes();

    /**
     * @return The routes, in the order the router registers them
     */
    PrecompiledRoute[] routes();
}
