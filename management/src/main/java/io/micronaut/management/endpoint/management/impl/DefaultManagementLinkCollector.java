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

@Singleton
@Requires(beans = ManagementEndpoint.class)
public class DefaultManagementLinkCollector implements ManagementLinkCollector {

    private final EmbeddedServer server;

    public DefaultManagementLinkCollector(EmbeddedServer server) {
        this.server = server;
    }

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

    public String getEndpointId(UriRoute route) {
        Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
        Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
        return StringUtils.isNotEmpty(endpoint.value()) ? endpoint.value() : endpoint.id();
    }
}
