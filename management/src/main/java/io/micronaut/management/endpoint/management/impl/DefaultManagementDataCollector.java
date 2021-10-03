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
import io.micronaut.http.hateoas.AbstractResource;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementDataCollector;
import io.micronaut.management.endpoint.management.ManagementController;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.UriRoute;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;

import java.util.*;
import java.util.stream.Stream;

/**
 * <p>Default {@link ManagementDataCollector} implementation. The full paths
 * to endpoints are provided to be clickable.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 */
@Singleton
@Requires(beans = ManagementController.class)
public class DefaultManagementDataCollector implements ManagementDataCollector<Resource> {

    /**
     * <p>Collect management endpoints in a {@link Resource}. Duplicate route paths are not registered because
     * Http methods are not shown on a resource, which makes identical route paths seemingly the same. Each entry
     * has the id of the Endpoint as key, except for the management route, which its key is <code>self</code>.</p>
     *
     * @param routes management routes to collect.
     * @param routeBase base route of the endpoints.
     * @param managementDiscoveryPath path of management endpoint.
     * @param isManagementDiscoveryPathTemplated whether the discovery path is templated.
     * @return {@link Publisher} with the Resource.
     */
    @Override
    public Resource collectData(Stream<UriRoute> routes, String routeBase,
                                           String managementDiscoveryPath, boolean isManagementDiscoveryPathTemplated) {
        AbstractResource<AvailableEndpoints> resource = new AvailableEndpoints();

        resource.link(Link.SELF, Link.build(routeBase + managementDiscoveryPath)
                .templated(isManagementDiscoveryPathTemplated).build());

        Set<String> collectedPaths = new HashSet<>();
        routes.forEach(route -> {
            UriMatchTemplate uriMatchTemplate = route.getUriMatchTemplate();
            String path = uriMatchTemplate.toString();

            // Since Http methods are not shown on a Resource, avoid identical
            // URLs, since they only differ on their Http method.
            if (collectedPaths.contains(path)) {
                return;
            }

            CharSequence endpointId = getEndpointId(route);

            // Ignore endpoints with a null id.
            if (Objects.isNull(endpointId)) {
                return;
            }

            boolean isTemplated = !uriMatchTemplate.getVariables().isEmpty();
            resource.link(endpointId, Link.build(routeBase + path).templated(isTemplated).build());

            collectedPaths.add(path);
        });

        return resource;
    }

    private String getEndpointId(UriRoute route) {
        if (route instanceof MethodBasedRoute) {
            return ((MethodBasedRoute) route).getTargetMethod()
                    .getAnnotationMetadata().stringValue(Endpoint.class).orElse(null);
        }
        throw new IllegalArgumentException("Route must be an instance of " + MethodBasedRoute.class.getName());
    }
}
