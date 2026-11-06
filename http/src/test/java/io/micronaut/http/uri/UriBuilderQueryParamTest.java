package io.micronaut.http.uri;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link UriBuilder#queryParam(String, Object...)} with {@link Iterable} and array values.
 */
class UriBuilderQueryParamTest {

    @Test
    void listValueIsExpandedIntoMultipleValues() {
        assertEquals("/foo?foo=bar&foo=baz",
            UriBuilder.of("/foo").queryParam("foo", List.of("bar", "baz")).toString());
    }

    @Test
    void setValueIsExpandedIntoMultipleValues() {
        Set<String> values = new LinkedHashSet<>(List.of("bar", "baz"));
        assertEquals("/foo?foo=bar&foo=baz",
            UriBuilder.of("/foo").queryParam("foo", values).toString());
    }

    @Test
    void nonStringIterableElementsUseToString() {
        assertEquals("/foo?foo=1&foo=2&foo=3",
            UriBuilder.of("/foo").queryParam("foo", List.of(1, 2, 3)).toString());
    }

    @Test
    void arrayValueIsExpandedIntoMultipleValues() {
        // a lone String[] argument would be the varargs array itself, so pass it as a single element
        Object[] values = {new String[]{"bar", "baz"}};
        assertEquals("/foo?foo=bar&foo=baz",
            UriBuilder.of("/foo").queryParam("foo", values).toString());

        Object[] integers = {new Integer[]{1, 2}};
        assertEquals("/foo?foo=1&foo=2",
            UriBuilder.of("/foo").queryParam("foo", integers).toString());
    }

    @Test
    void scalarIterableAndArrayValuesAreFlattenedInOrder() {
        assertEquals("/foo?foo=a&foo=b&foo=c&foo=d&foo=e&foo=f",
            UriBuilder.of("/foo")
                .queryParam("foo", "a", List.of("b", "c"), "d", new String[]{"e", "f"})
                .toString());
    }

    @Test
    void nullElementsInsideIterableAreSkipped() {
        assertEquals("/foo?foo=a&foo=b",
            UriBuilder.of("/foo").queryParam("foo", Arrays.asList("a", null, "b")).toString());
    }

    @Test
    void nullElementsInsideArrayAreSkipped() {
        Object[] values = {new String[]{"a", null, "b"}};
        assertEquals("/foo?foo=a&foo=b",
            UriBuilder.of("/foo").queryParam("foo", values).toString());
    }

    @Test
    void emptyIterableAddsNoQueryParam() {
        assertEquals("/foo", UriBuilder.of("/foo").queryParam("foo", List.of()).toString());
    }

    @Test
    void allNullIterableAddsNoQueryParam() {
        assertEquals("/foo", UriBuilder.of("/foo").queryParam("foo", Arrays.asList(null, null)).toString());
    }

    @Test
    void emptyArrayAlongsideScalarKeepsOnlyScalar() {
        assertEquals("/foo?foo=bar",
            UriBuilder.of("/foo").queryParam("foo", new String[0], "bar").toString());
    }

    @Test
    void iterableValuesAppendToExistingValues() {
        UriBuilder builder = UriBuilder.of("/foo?foo=existing");
        builder.queryParam("foo", List.of("a", "b"));
        builder.queryParam("foo", List.of("c"));
        assertEquals("/foo?foo=existing&foo=a&foo=b&foo=c", builder.toString());
    }

    @Test
    void iterableElementsAreEncoded() {
        assertEquals("/foo?foo=hello+world&foo=a%26b",
            UriBuilder.of("/foo").queryParam("foo", List.of("hello world", "a&b")).toString());
    }

    @Test
    void iterableValuesForDifferentNamesAreKeptSeparate() {
        assertEquals("/foo?a=1&a=2&b=3",
            UriBuilder.of("/foo")
                .queryParam("a", List.of(1, 2))
                .queryParam("b", List.of(3))
                .toString());
    }
}
