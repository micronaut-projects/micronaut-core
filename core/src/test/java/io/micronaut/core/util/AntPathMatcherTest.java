package io.micronaut.core.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AntPathMatcherTest {

    // the expectations are the results of the matcher before the segments without a '*' were
    // compared without copying them to arrays: the quirks are kept (empty and trimmed segments)
    @ParameterizedTest
    @MethodSource("cases")
    void matches(String pattern, String path, boolean expected) {
        assertEquals(expected, new AntPathMatcher().matches(pattern, path));
    }

    private static Stream<Arguments> cases() {
        return Stream.of(
            Arguments.of("/c/filtered", "/c/filtered", true),
            Arguments.of("/c/filtered", "/c//filtered", true),
            Arguments.of("/c/filtered", "/c/filtered/", false),
            Arguments.of("/c/filtered/", "/c/filtered/", true),
            Arguments.of("/c/f?ltered", "/c/filtered", true),
            Arguments.of("/c/*", "/c/x", true),
            Arguments.of("/c/*", "/c/x/y", false),
            Arguments.of("/c/**", "/c/x/y", true),
            Arguments.of("/c/a b", "/c/a b", true),
            Arguments.of("/c/a b ", "/c/a b", true),
            Arguments.of("/c/ab", "/c/a b", false),
            Arguments.of("/a/b", "/a", false),
            Arguments.of("/a", "/a/b", false),
            Arguments.of("/", "/", true),
            Arguments.of("/*.json", "/x.json", true),
            Arguments.of("/c/?", "/c/", false),
            Arguments.of("/c/?", "/c/xy", false),
            Arguments.of("/a*c", "/abc", true)
        );
    }
}
