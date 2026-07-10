package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {
    @Test
    void byteCount() {
        StringBuilder sb = new StringBuilder();
        while (sb.toString().getBytes(StandardCharsets.UTF_8).length < 4096) {
            sb.append('a');
        }
        assertEquals(4096, StringUtils.utf8Bytes(sb.toString()));
    }

    @ParameterizedTest
    @MethodSource("trimTrailingCharacterArguments")
    void trimTrailingCharacter(String input, char character, String expected) {
        assertEquals(expected, StringUtils.trimTrailingCharacter(input, character));
    }

    private static Stream<Arguments> trimTrailingCharacterArguments() {
        return Stream.of(
            Arguments.of("abc", 'c', "ab"),
            Arguments.of("abc", 'd', "abc"),
            Arguments.of("abc   ", ' ', "abc"),
            Arguments.of("aa", 'a', "a"),
            Arguments.of("", 'a', ""),
            Arguments.of(null, 'a', null)
        );
    }

    @ParameterizedTest
    @MethodSource("trimTrailingSlashExceptRootArguments")
    void trimTrailingSlashExceptRoot(String input, String expected) {
        assertEquals(expected, StringUtils.trimTrailingSlashExceptRoot(input));
    }

    private static Stream<Arguments> trimTrailingSlashExceptRootArguments() {
        return Stream.of(
            Arguments.of("/admin/secret/", "/admin/secret"),
            Arguments.of("/admin/secret", "/admin/secret"),
            Arguments.of("path/", "path"),
            Arguments.of("path//", "path/"),
            Arguments.of("/", "/"),
            Arguments.of(null, null)
        );
    }
}
