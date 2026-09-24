package io.micronaut.http.server.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.RouteMetadataHolder;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Netty request and response keep the metadata of the matched route in typed fields, and
 * expose it both through {@link RouteMetadataHolder} and through the attribute map.
 */
@SuppressWarnings("removal")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteMetadataHolderTest {
    static final String SPEC_NAME = "RouteMetadataHolderTest";

    private EmbeddedServer server;
    private HttpClient client;

    @BeforeAll
    void startServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of("spec.name", SPEC_NAME));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURI());
    }

    @AfterAll
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void routeMetadataIsExposedThroughHolderAndAttributes() {
        CapturingFilter filter = server.getApplicationContext().getBean(CapturingFilter.class);

        assertEquals("id 123", client.toBlocking().retrieve("/route-metadata/foo/123"));

        HttpRequest<?> request = filter.request;
        MutableHttpResponse<?> response = filter.response;
        assertInstanceOf(NettyHttpRequest.class, request);
        assertInstanceOf(NettyMutableHttpResponse.class, response);

        // typed access, before the attribute map exists
        RouteMetadataHolder requestHolder = (RouteMetadataHolder) request;
        UriRouteMatch<?, ?> routeMatch = assertInstanceOf(UriRouteMatch.class, requestHolder.getRouteMatchMetadata());
        RouteInfo<?> routeInfo = assertInstanceOf(RouteInfo.class, requestHolder.getRouteInfoMetadata());
        assertEquals("/route-metadata/foo/{id}", requestHolder.getUriTemplateMetadata());
        assertSame(routeMatch.getRouteInfo(), routeInfo);
        assertSame(routeMatch, RouteAttributes.getRouteMatch(request).orElseThrow());
        assertSame(routeInfo, RouteAttributes.getRouteInfo(request).orElseThrow());
        assertEquals("/route-metadata/foo/{id}", BasicHttpAttributes.getUriTemplate(request).orElseThrow());
        assertSame(routeMatch, request.getAttribute(HttpAttributes.ROUTE_MATCH).orElseThrow());
        assertSame(routeInfo, request.getAttribute(HttpAttributes.ROUTE_INFO).orElseThrow());
        assertEquals("/route-metadata/foo/{id}", request.getAttribute(HttpAttributes.URI_TEMPLATE).orElseThrow());

        RouteMetadataHolder responseHolder = (RouteMetadataHolder) response;
        assertSame(routeMatch, responseHolder.getRouteMatchMetadata());
        assertSame(routeInfo, responseHolder.getRouteInfoMetadata());
        assertSame(routeMatch, RouteAttributes.getRouteMatch(response).orElseThrow());
        assertSame(routeInfo, RouteAttributes.getRouteInfo(response).orElseThrow());

        // the attribute map, once created, exposes the same metadata
        assertTrue(request.getAttributes().contains(HttpAttributes.ROUTE_MATCH.toString()));
        assertSame(routeMatch, request.getAttributes().get(HttpAttributes.ROUTE_MATCH.toString(), RouteMatch.class).orElseThrow());
        assertSame(routeInfo, request.getAttributes().get(HttpAttributes.ROUTE_INFO.toString(), RouteInfo.class).orElseThrow());
        assertEquals("/route-metadata/foo/{id}", request.getAttributes().get(HttpAttributes.URI_TEMPLATE.toString(), String.class).orElseThrow());
        assertSame(routeMatch, request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class).orElseThrow());
        assertSame(routeMatch, response.getAttributes().get(HttpAttributes.ROUTE_MATCH.toString(), RouteMatch.class).orElseThrow());
        assertSame(routeInfo, response.getAttributes().get(HttpAttributes.ROUTE_INFO.toString(), RouteInfo.class).orElseThrow());

        // and the typed access keeps working after the map was created, in both directions
        assertSame(routeMatch, requestHolder.getRouteMatchMetadata());
        assertSame(routeInfo, requestHolder.getRouteInfoMetadata());
        assertEquals("/route-metadata/foo/{id}", requestHolder.getUriTemplateMetadata());
        assertSame(routeMatch, responseHolder.getRouteMatchMetadata());
        assertSame(routeInfo, responseHolder.getRouteInfoMetadata());
        BasicHttpAttributes.setUriTemplate(request, "/other");
        assertEquals("/other", request.getAttributes().get(HttpAttributes.URI_TEMPLATE.toString(), String.class).orElseThrow());
        request.getAttributes().remove(HttpAttributes.URI_TEMPLATE.toString());
        assertTrue(BasicHttpAttributes.getUriTemplate(request).isEmpty());
    }

    @Test
    void mutableViewSharesRouteMetadata() {
        CapturingFilter filter = server.getApplicationContext().getBean(CapturingFilter.class);

        assertEquals("id 456", client.toBlocking().retrieve("/route-metadata/foo/456"));

        HttpRequest<?> request = filter.request;
        HttpRequest<?> view = request.mutate();
        assertInstanceOf(RouteMetadataHolder.class, view);
        RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(view).orElseThrow();
        assertSame(RouteAttributes.getRouteMatch(request).orElseThrow(), routeMatch);
        assertSame(routeMatch, view.getAttribute(HttpAttributes.ROUTE_MATCH).orElseThrow());
        assertEquals("/route-metadata/foo/{id}", BasicHttpAttributes.getUriTemplate(view).orElseThrow());

        view.setAttribute(HttpAttributes.URI_TEMPLATE, "/from-view");
        assertEquals("/from-view", BasicHttpAttributes.getUriTemplate(request).orElseThrow());
        assertEquals("/from-view", view.getAttributes().get(HttpAttributes.URI_TEMPLATE.toString(), String.class).orElseThrow());
    }

    @Controller("/route-metadata")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RouteMetadataController {
        @Get("/foo/{id}")
        String foo(String id) {
            return "id " + id;
        }
    }

    @ServerFilter("/route-metadata/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    static class CapturingFilter {
        volatile HttpRequest<?> request;
        volatile MutableHttpResponse<?> response;

        @ResponseFilter
        void capture(HttpRequest<?> request, MutableHttpResponse<?> response) {
            this.request = request;
            this.response = response;
        }
    }
}
