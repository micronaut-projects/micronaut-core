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
package io.micronaut.context.python;

import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.writer.BeanDefinitionDescriptors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The definitions the Python compile of the build writes, from {@code src/main/python} and {@code src/test/python},
 * carry descriptors that agree with their classes, as the ones javac writes do, and sit on one class path with the
 * empty entries of a module released before descriptors existed.
 */
class PythonBeanDefinitionDescriptorsTest {

    private static final String ENTRIES = "META-INF/micronaut/" + BeanDefinitionReference.class.getName();

    /**
     * Compiled from a source root of {@code src/main/python}, and from {@code src/test/python} with a qualifier that
     * Python declares.
     */
    private static final List<String> PYTHON = List.of(
        "micronaut.docs.sourceroots.$GreetingService$Definition",
        "micronaut.docs.qualifiers.annotationmember.$V8Engine$Definition"
    );

    /**
     * Of micronaut-session, from Maven Central at the version of the catalog, which was built before micronaut-core 5.3.
     */
    private static final List<String> RELEASED = List.of(
        "io.micronaut.session.$InMemorySessionStore$Definition",
        "io.micronaut.session.http.$HttpSessionFilter$Definition"
    );

    private static BeanDefinitionDescriptors.Comparison comparison;

    @BeforeAll
    static void compare() {
        comparison = BeanDefinitionDescriptors.compareAll(PythonBeanDefinitionDescriptorsTest.class.getClassLoader());
    }

    @Test
    void theDescriptorOfEveryDefinitionThePythonCompileWroteAgreesWithItsClass() throws IOException, URISyntaxException {
        Set<String> compiled = compiledFromPython();

        assertTrue(compiled.containsAll(PYTHON), () -> "compiled from Python: " + compiled);
        assertEquals(Map.of(), comparison.getDifferences(), comparison::toString);
        List<String> notCompared = new ArrayList<>(compiled);
        notCompared.removeAll(comparison.getCompared());
        assertEquals(List.of(), notCompared, comparison::toString);
    }

    @Test
    void aDefinitionOfAModuleReleasedBeforeDescriptorsHasAnEmptyEntry() {
        assertTrue(comparison.getWithoutDescriptor().containsAll(RELEASED),
            () -> "micronaut-session now ships descriptors: pin a release built before micronaut-core 5.3 as the fixture. "
                + comparison);
    }

    /**
     * @return The definitions whose entries are in the directories the Python compile writes to
     */
    private static Set<String> compiledFromPython() throws IOException, URISyntaxException {
        Set<String> names = new TreeSet<>();
        for (URL directory : Collections.list(PythonBeanDefinitionDescriptorsTest.class.getClassLoader().getResources(ENTRIES))) {
            if ("file".equals(directory.getProtocol()) && directory.getPath().contains("/classes/python/")) {
                try (Stream<Path> entries = Files.list(Path.of(directory.toURI()))) {
                    entries.forEach(entry -> names.add(entry.getFileName().toString()));
                }
            }
        }
        return names;
    }
}
