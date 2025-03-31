package io.micronaut.http.cachecontrol;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScopeTest {
    @ParameterizedTest
    @MethodSource("toStringTestCases")
    void testToString(ResponseDirective input, String expected) {
        assertEquals(expected, input.toString());
    }

    static Stream<Arguments> toStringTestCases() {
        return Stream.of(
            Arguments.of(ResponseDirective.MAX_AGE, "max-age"),
            Arguments.of(ResponseDirective.S_MAXAGE, "s-maxage"),
            Arguments.of(ResponseDirective.NO_CACHE, "no-cache"),
            Arguments.of(ResponseDirective.MUST_REVALIDATE, "must-revalidate"),
            Arguments.of(ResponseDirective.PROXY_REVALIDATE, "proxy-revalidate"),
            Arguments.of(ResponseDirective.NO_STORE, "no-store"),
            Arguments.of(ResponseDirective.PRIVATE, "private"),
            Arguments.of(ResponseDirective.PUBLIC, "public"),
            Arguments.of(ResponseDirective.MUST_UNDERSTAND, "must-understand"),
            Arguments.of(ResponseDirective.NO_TRANSFORM, "no-transform"),
            Arguments.of(ResponseDirective.IMMUTABLE, "immutable"),
            Arguments.of(ResponseDirective.STALE_WHILE_REVALIDATE, "stale-while-revalidate"),
            Arguments.of(ResponseDirective.STALE_IF_ERROR, "stale-if-error")
        );
    }
}
