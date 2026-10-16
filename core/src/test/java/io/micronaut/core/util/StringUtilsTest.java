package io.micronaut.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.StringTokenizer;
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
            Arguments.of("abc ", ' ', "abc"),
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

    @Test
    void tokenizeToStringArrayKeepsTheTokenizerSemantics() {
        // trimmed tokens, no empty ones, any of the delimiter characters separates, like the
        // StringTokenizer it replaces for the ASCII delimiters
        char[] alphabet = {'/', '/', 'a', 'b', ' ', '*', '?', '\t', '{', '}', '-', '.', '\u0001', '\u00e9'};
        String[] delimiters = {"/", "/ ", ",", ", \t"};
        Random random = new Random(42);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder sb = new StringBuilder();
            for (int n = random.nextInt(14); n > 0; n--) {
                sb.append(alphabet[random.nextInt(alphabet.length)]);
            }
            String str = sb.toString();
            String delimiter = delimiters[random.nextInt(delimiters.length)];
            StringTokenizer tokenizer = new StringTokenizer(str, delimiter);
            List<String> expected = new ArrayList<>();
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken().trim();
                if (!token.isEmpty()) {
                    expected.add(token);
                }
            }
            assertArrayEquals(expected.toArray(new String[0]), StringUtils.tokenizeToStringArray(str, delimiter), "[" + str + "] by [" + delimiter + "]");
        }
    }
}
