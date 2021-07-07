package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.hateoas.AbstractResource;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementEndpoint;
import io.micronaut.management.endpoint.management.ManagementLinkCollector;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>Default {@link ManagementLinkCollector} implementation. The full paths
 * to endpoints are provided to be clickable.</p>
 *
 * @author Hernán Cervera
 * @since 2.5
 */
@Singleton
@Requires(beans = ManagementEndpoint.class)
public class DefaultManagementLinkCollector implements ManagementLinkCollector {

    private final EmbeddedServer server;

    public DefaultManagementLinkCollector(EmbeddedServer server) {
        this.server = server;
    }

    /**
     * <p>Collect management endpoints in a {@link Resource}. Duplicate route paths are not registered because
     * Http methods are not shown on a resource, which makes identical route paths seemingly the same. Each entry
     * has the id of the Endpoint as key, except for the management route, which its key is <code>self</code>.</p>
     *
     * @param routes management routes to collect.
     * @param selfEndpointId id of the {@link Endpoint} which returns the {@link Resource}.
     * @return the {@link Resource} with the links to the management endpoints.
     */
    @Override
    public Resource collectLinks(Stream<UriRoute> routes, String selfEndpointId) {
        String routeBase = String.format("%s://%s:%d",
                server.getScheme(), server.getHost(), server.getPort());

        AbstractResource<ManagementResponse> resource = new ManagementResponse();

        Set<String> collectedPaths = new HashSet<>();
        routes.forEach(route -> {
            String path = route.getUriMatchTemplate().toString();

            // Since Http methods are not shown on a Resource, avoid identical
            // URLs, since they only differ on their Http method.
            if (collectedPaths.contains(route.getUriMatchTemplate().toString())) {
                return;
            }

            CharSequence endpointId = getEndpointId(route);
            CharSequence linkRef = endpointId;
            if (endpointId.equals(selfEndpointId)) {
                linkRef = Link.SELF;
            }
            boolean isTemplated = !route.getUriMatchTemplate().getVariables().isEmpty();
            resource.link(linkRef,
                    Link.build(routeBase + path)
                            .templated(isTemplated)
                            .build());

            collectedPaths.add(path);
        });

        return resource;
    }

    private String getEndpointId(UriRoute route) {
        Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
        Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
        return StringUtils.isNotEmpty(endpoint.value()) ? endpoint.value() : endpoint.id();
    }
}
