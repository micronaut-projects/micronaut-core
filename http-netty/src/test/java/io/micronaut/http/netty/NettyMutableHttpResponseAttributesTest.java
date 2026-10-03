package io.micronaut.http.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.RouteMetadataHolder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The route metadata of a {@link NettyMutableHttpResponse} is stored in typed fields only, which
 * the attribute map, once requested, reads and writes through.
 */
@SuppressWarnings("removal")
class NettyMutableHttpResponseAttributesTest {

    @Test
    void routeMetadataIsExposedThroughHolderAndAttributes() {
        NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
        RouteMetadataHolder holder = response;
        Object routeMatch = new Object();
        Object routeInfo = new Object();

        assertNull(holder.getRouteMatchMetadata());
        assertTrue(response.getAttribute(HttpAttributes.ROUTE_MATCH).isEmpty());
        assertTrue(response.getAttribute("other").isEmpty());

        holder.setRouteMatchMetadata(routeMatch);
        response.setAttribute(HttpAttributes.ROUTE_INFO, routeInfo);
        response.setAttribute(HttpAttributes.URI_TEMPLATE, "/foo/{id}");

        assertSame(routeMatch, holder.getRouteMatchMetadata());
        assertSame(routeInfo, holder.getRouteInfoMetadata());
        assertEquals("/foo/{id}", holder.getUriTemplateMetadata());
        assertSame(routeMatch, response.getAttribute(HttpAttributes.ROUTE_MATCH).orElseThrow());
        assertSame(routeInfo, response.getAttribute(HttpAttributes.ROUTE_INFO).orElseThrow());
        assertEquals("/foo/{id}", response.getAttribute(HttpAttributes.URI_TEMPLATE).orElseThrow());

        // the map is created on demand and exposes the metadata
        assertSame(routeMatch, response.getAttributes().getValue(HttpAttributes.ROUTE_MATCH.toString()));
        assertSame(routeInfo, response.getAttributes().get(HttpAttributes.ROUTE_INFO.toString(), Object.class).orElseThrow());
        assertEquals("/foo/{id}", response.getAttribute(HttpAttributes.URI_TEMPLATE, String.class).orElseThrow());
        assertEquals(3, response.getAttributes().names().size());
        assertSame(response.getAttributes(), response.getAttributes());

        // both views stay consistent afterwards
        assertSame(routeMatch, holder.getRouteMatchMetadata());
        holder.setRouteMatchMetadata(null);
        assertFalse(response.getAttributes().contains(HttpAttributes.ROUTE_MATCH.toString()));
        response.getAttributes().put(HttpAttributes.URI_TEMPLATE.toString(), "/bar");
        assertEquals("/bar", holder.getUriTemplateMetadata());
        response.setAttribute("other", "value");
        assertEquals("value", response.getAttribute("other").orElseThrow());
        assertEquals(Set.of(HttpAttributes.ROUTE_INFO.toString(), HttpAttributes.URI_TEMPLATE.toString(), "other"),
            response.getAttributes().names());
        Map<String, Object> entries = new HashMap<>();
        response.getAttributes().forEach(entries::put);
        assertEquals(Map.of(HttpAttributes.ROUTE_INFO.toString(), routeInfo, HttpAttributes.URI_TEMPLATE.toString(), "/bar", "other", "value"), entries);
        response.getAttributes().remove(HttpAttributes.ROUTE_INFO.toString());
        assertNull(holder.getRouteInfoMetadata());
        response.getAttributes().clear();
        assertNull(holder.getUriTemplateMetadata());
        assertTrue(response.getAttributes().isEmpty());
        assertTrue(response.getAttribute("other").isEmpty());
    }

    @Test
    void otherAttributesCreateTheMap() {
        NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
        response.setAttribute("other", "value");
        response.setAttribute(HttpAttributes.URI_TEMPLATE, "/foo");
        assertEquals("value", response.getAttributes().getValue("other"));
        assertEquals("/foo", response.getAttributes().getValue(HttpAttributes.URI_TEMPLATE.toString()));
        assertEquals("/foo", response.getUriTemplateMetadata());
    }

    @Test
    void readingAttributesDoesNotCreateTheMap() throws ReflectiveOperationException {
        NettyMutableHttpResponse<Object> response = new NettyMutableHttpResponse<>(ConversionService.SHARED);
        HttpResponse<?> wrapper = new HttpResponseWrapper<>(new HttpResponseWrapper<>(response));

        assertTrue(response.getAttribute("skip", Boolean.class).isEmpty());
        assertTrue(wrapper.getAttribute("skip", Boolean.class).isEmpty());
        assertTrue(wrapper.getAttribute("skip").isEmpty());
        assertTrue(wrapper.getAttribute(HttpAttributes.ROUTE_MATCH, Object.class).isEmpty());
        assertNull(attributeMap(response));

        Object routeMatch = new Object();
        response.setRouteMatchMetadata(routeMatch);
        response.setUriTemplateMetadata("/foo");
        assertSame(routeMatch, wrapper.getAttribute(HttpAttributes.ROUTE_MATCH, Object.class).orElseThrow());
        assertSame(routeMatch, wrapper.getAttribute(HttpAttributes.ROUTE_MATCH).orElseThrow());
        assertEquals("/foo", wrapper.getAttribute(HttpAttributes.URI_TEMPLATE, String.class).orElseThrow());
        assertNull(attributeMap(response));

        // other attributes create the map, and the typed lookup converts their value
        response.setAttribute("skip", "true");
        assertEquals(Boolean.TRUE, wrapper.getAttribute("skip", Boolean.class).orElseThrow());
        assertEquals("true", wrapper.getAttribute("skip").orElseThrow());
        assertSame(routeMatch, wrapper.getAttribute(HttpAttributes.ROUTE_MATCH, Object.class).orElseThrow());
    }

    private static Object attributeMap(NettyMutableHttpResponse<?> response) throws ReflectiveOperationException {
        Field field = NettyMutableHttpResponse.class.getDeclaredField("attributes");
        field.setAccessible(true);
        return field.get(response);
    }
}
