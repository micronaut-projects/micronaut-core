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

class SiteTest {

    /**
     * The site values the specification lists, so that this enum drifting away from the
     * specification fails here rather than silently dropping metadata at runtime.
     *
     * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-site-header">Site</a>
     */
    private static final Set<String> SPECIFICATION_VALUES = Set.of(
        "cross-site",
        "same-origin",
        "same-site",
        "none"
    );

    @Test
    void listsExactlyTheSiteValuesOfTheSpecification() {
        Set<String> declared = Stream.of(Site.values())
            .map(Site::toString)
            .collect(Collectors.toSet());

        assertEquals(SPECIFICATION_VALUES, declared);
    }

    @ParameterizedTest
    @MethodSource
    void findsSiteFromWireValue(String value, Site expected) {
        assertEquals(expected, Site.of(value));
    }

    private static Stream<Arguments> findsSiteFromWireValue() {
        return Stream.of(Site.values())
            .map(site -> Arguments.of(site.toString(), site));
    }

    @ParameterizedTest
    @MethodSource
    void returnsNullForUnknownSiteValues(String value) {
        assertNull(Site.of(value));
    }

    private static Stream<String> returnsNullForUnknownSiteValues() {
        return Stream.of("unknown", null);
    }
}
