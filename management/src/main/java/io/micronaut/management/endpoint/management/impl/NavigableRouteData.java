package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.management.ManagementEndpoint;
import io.micronaut.management.endpoint.routes.RouteData;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.UriRoute;

import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
@Requires(beans = ManagementEndpoint.class)
public class NavigableRouteData implements RouteData<Map<String, String>> {

    private final String host;
    private final int port;
    private final String scheme;

    public NavigableRouteData(EmbeddedServer embeddedServer) {
        host = embeddedServer.getHost();
        port = embeddedServer.getPort();
        scheme = embeddedServer.getScheme();
    }

    @Override
    public Map<String, String> getData(UriRoute route) {
        Map<String, String> values = new LinkedHashMap<>(1);
        values.put("href", getAbsoluteRoute(route));
        return values;
    }

    public String getAbsoluteRoute(UriRoute route) {
        String path = route.getUriMatchTemplate().toPathString();
        return String.format("%s://%s:%d%s", scheme, host, port, path);
    }
}
