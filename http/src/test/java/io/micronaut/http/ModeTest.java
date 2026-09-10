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
package io.micronaut.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModeTest {

    /**
     * The mode values the specification lists, so that this enum drifting away from the
     * specification fails here rather than silently dropping metadata at runtime.
     *
     * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-mode-header">Mode</a>
     */
    private static final Set<String> SPECIFICATION_VALUES = Set.of(
        "same-origin",
        "no-cors",
        "cors",
        "navigate",
        "websocket"
    );

    @Test
    void listsExactlyTheModeValuesOfTheSpecification() {
        Set<String> declared = Stream.of(Mode.values())
            .map(Mode::toString)
            .collect(Collectors.toSet());

        assertEquals(SPECIFICATION_VALUES, declared);
    }

    @ParameterizedTest
    @MethodSource
    void findsModeFromWireValue(String value, Mode expected) {
        assertEquals(expected, Mode.of(value));
    }

    private static Stream<Arguments> findsModeFromWireValue() {
        return Stream.of(Mode.values())
            .map(mode -> Arguments.of(mode.toString(), mode));
    }

    @ParameterizedTest
    @MethodSource
    void returnsNullForUnknownModeValues(String value) {
        assertNull(Mode.of(value));
    }

    private static Stream<String> returnsNullForUnknownModeValues() {
        return Stream.of("unknown", null);
    }
}
