/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.http.annotation.Controller;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Java port of TypeElementVisitorProcessorSpec.
 */
class TypeElementVisitorProcessorTest {

    @Test
    void testGetAnnotationNames() {
        Set<String> visitedAnnotationNames = TypeElementVisitorProcessor.getVisitedAnnotationNames();

        // Basic sanity
        assertNotNull(visitedAnnotationNames);
        assertFalse(visitedAnnotationNames.isEmpty());

        // Must include some well-known visitors
        // Ensure at least one well-known visitor annotation is present (Controller)
        assertTrue(visitedAnnotationNames.contains(Controller.class.getName()));

        // Should never include wildcard marker
        assertFalse(visitedAnnotationNames.contains("*"));
    }
}
