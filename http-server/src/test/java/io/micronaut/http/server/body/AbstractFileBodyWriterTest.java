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
package io.micronaut.http.server.body;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFileBodyWriterTest {

    @Test
    void ifNoneMatchIsAListOfEntityTags() {
        String tag = "\"abc-1\"";
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("\"abc-1\""), tag));
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("W/\"abc-1\""), tag), "weak comparison");
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("\"x\", \"abc-1\""), tag));
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("*"), tag));
        // a tag of a second field line
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("\"x\"", "\"abc-1\""), tag));
        // a comma inside the quotes of a tag
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("\"a,b\", \"abc-1\""), tag));
        assertTrue(AbstractFileBodyWriter.matchesAny(List.of("\"a,b\""), "\"a,b\""));
        assertFalse(AbstractFileBodyWriter.matchesAny(List.of("\"a,b\""), "\"a\""));
        assertFalse(AbstractFileBodyWriter.matchesAny(List.of("\"abc-2\""), tag));
        assertFalse(AbstractFileBodyWriter.matchesAny(List.of("abc-1"), tag), "unquoted");
        assertFalse(AbstractFileBodyWriter.matchesAny(List.of("\"abc-1"), tag), "no closing quote");
        assertFalse(AbstractFileBodyWriter.matchesAny(List.of(""), tag));
    }
}
