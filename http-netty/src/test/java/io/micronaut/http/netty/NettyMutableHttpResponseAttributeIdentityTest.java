package io.micronaut.http.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.attr.MutableAttributeHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading an attribute as a type the stored value already has returns the stored value.
 */
class NettyMutableHttpResponseAttributeIdentityTest {

    @Test
    void responseReturnsStoredValues() {
        assertValuesOfRequiredTypeAreNotCopied(new NettyMutableHttpResponse<>(ConversionService.SHARED));
    }

    private static void assertValuesOfRequiredTypeAreNotCopied(MutableAttributeHolder holder) {
        Map<String, Object> map = new LinkedHashMap<>();
        holder.setAttribute("map", map);
        holder.getAttribute("map", Map.class).orElseThrow().put("a", 1);
        assertSame(map, holder.getAttribute("map", Map.class).orElseThrow());
        assertSame(map, holder.getAttribute("map", LinkedHashMap.class).orElseThrow());
        assertEquals(1, holder.getAttribute("map", Map.class).orElseThrow().get("a"));

        List<String> list = new ArrayList<>();
        holder.setAttribute("list", list);
        holder.getAttribute("list", List.class).orElseThrow().add("a");
        assertSame(list, holder.getAttribute("list", List.class).orElseThrow());
        assertSame(list, holder.getAttribute("list", Collection.class).orElseThrow());
        assertSame(list, holder.getAttribute("list", Iterable.class).orElseThrow());
        assertEquals(List.of("a"), holder.getAttribute("list", List.class).orElseThrow());

        StringBuilder plain = new StringBuilder();
        holder.setAttribute("plain", plain);
        assertSame(plain, holder.getAttribute("plain", StringBuilder.class).orElseThrow());
        assertSame(plain, holder.getAttribute("plain", CharSequence.class).orElseThrow());

        holder.setAttribute("number", 5);
        assertEquals(5, holder.getAttribute("number", int.class).orElseThrow());
        assertEquals(5, holder.getAttribute("number", Integer.class).orElseThrow());
        // a value of another type is still converted
        assertEquals("5", holder.getAttribute("number", String.class).orElseThrow());
        assertEquals(5L, holder.getAttribute("number", Long.class).orElseThrow());
        assertTrue(holder.getAttribute("missing", Map.class).isEmpty());

        // a CharSequence name that is not a String reads the same entry
        holder.setAttribute(new StringBuilder("builder-key"), map);
        assertSame(map, holder.getAttribute(new StringBuilder("builder-key"), Map.class).orElseThrow());
    }
}
