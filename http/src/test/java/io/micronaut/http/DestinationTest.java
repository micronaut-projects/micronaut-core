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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DestinationTest {

    @ParameterizedTest
    @MethodSource
    void findsDestinationFromWireValue(String value, Destination expected) {
        assertEquals(expected, Destination.of(value));
    }

    private static Stream<Arguments> findsDestinationFromWireValue() {
        return Stream.of(Destination.values())
            .map(destination -> Arguments.of(destination.toString(), destination));
    }

    @ParameterizedTest
    @MethodSource
    void returnsNullForUnknownDestinationValues(String value) {
        assertNull(Destination.of(value));
    }

    private static Stream<String> returnsNullForUnknownDestinationValues() {
        return Stream.of("unknown", null);
    }
}
