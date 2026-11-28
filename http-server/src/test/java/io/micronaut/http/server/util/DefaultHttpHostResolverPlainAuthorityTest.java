/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fast path of the host value validation skips the URI parse only for values that the parse accepts.
 */
class DefaultHttpHostResolverPlainAuthorityTest {

    // the characters of the fast path, weighted, and characters it must leave to the URI parse
    private static final String CHARACTERS = "abcxyzABCXYZ0189-._~:".repeat(4) + "@/?#%[]!$&'()*+,;= \t\"<>\\^`{|}\u00e4\u0000";

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "example.com:8443", "my_service", "a~b", "-", ".", ":", "::", ":80", "a:", "1.2.3.4",
        "xn--bcher-kva.example", "A.B.C"})
    void aPlainValueIsAcceptedByTheUriParse(String value) {
        assertTrue(DefaultHttpHostResolver.isPlainAuthority(value));
        assertTrue(DefaultHttpHostResolver.isUriAuthority(value), value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "[::1]", "a@b", "a b", "a/b", "a?b", "a#b", "a%41", "a,b", "a;b", "a=b", "\u00e4.example", "a\tb"})
    void otherValuesTakeTheUriParse(String value) {
        assertFalse(DefaultHttpHostResolver.isPlainAuthority(value), value);
    }

    @Test
    void randomValuesTakeTheFastPathOnlyWhenTheUriParseAcceptsThem() {
        Random random = new Random(20261125L);
        int plain = 0;
        for (int i = 0; i < 200_000; i++) {
            StringBuilder value = new StringBuilder();
            int length = 1 + random.nextInt(6);
            for (int j = 0; j < length; j++) {
                value.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
            }
            String authority = value.toString();
            if (DefaultHttpHostResolver.isPlainAuthority(authority)) {
                plain++;
                assertTrue(DefaultHttpHostResolver.isUriAuthority(authority), authority);
            }
        }
        assertTrue(plain > 10_000, "too few values took the fast path: " + plain);
    }
}
