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
package io.micronaut.management.endpoint.management;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.management.endpoint.management.impl.DefaultManagementRoutesResolver;
import io.micronaut.web.router.UriRoute;

import java.util.stream.Stream;

/**
 * <p>Find the routes which are used by the {@link ManagementController}.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@DefaultImplementation(DefaultManagementRoutesResolver.class)
public interface ManagementRoutesResolver {

    /**
     * @return management routes.
     */
    Stream<UriRoute> getRoutes();
}
