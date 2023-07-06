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
package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementController;
import io.micronaut.management.endpoint.management.ManagementRoutesResolver;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import jakarta.inject.Singleton;

import java.util.stream.Stream;

/**
 * <p>Default {@link ManagementRoutesResolver} implementation.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Singleton
@Requires(beans = ManagementController.class)
public class DefaultManagementRoutesResolver implements ManagementRoutesResolver {

    private final Router router;

    public DefaultManagementRoutesResolver(Router router) {
        this.router = router;
    }

    /**
     * <p>Find routes which are backed by a method and which owning class
     * is marked with {@link Endpoint}.</p>
     *
     * @return The {@link UriRoute}s in a stream.
     */
    @Override
    public Stream<UriRoute> getRoutes() {
        return router.uriRoutes().filter(this::isManagementRoute);
    }

    private boolean isManagementRoute(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            return ((MethodBasedRoute) route).getTargetMethod()
                    .getAnnotationMetadata().hasAnnotation(Endpoint.class);
        }
        return false;
    }
}
