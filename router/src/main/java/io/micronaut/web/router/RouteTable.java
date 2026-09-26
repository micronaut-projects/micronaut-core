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
 * The internal table of the routes of located targets: the routes a
 * {@link io.micronaut.web.router.builder.LocatedRoutes} declares, relative to the prefix of the
 * locator, built by the {@link RouteTableFactory} when a locator locates the first target they
 * route, and matched by the {@link RouteLocator} with the rest of the path.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public sealed interface RouteTable permits DefaultRouteTable {
}
