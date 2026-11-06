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
package io.micronaut.dev;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevLoggingNamesTest {

    private static final String PROPERTY = "logback.configurationFile";

    private final String before = System.getProperty(PROPERTY);

    @AfterEach
    void restore() {
        if (before == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, before);
        }
    }

    @Test
    void theDefaultFilesAndTheFileTheSystemPropertyNamesConfigureLogback() {
        System.clearProperty(PROPERTY);
        assertTrue(DevLogging.isConfigurationName("logback.xml"));
        assertTrue(DevLogging.isConfigurationName("logback-test.xml"));
        assertFalse(DevLogging.isConfigurationName("custom.xml"));

        System.setProperty(PROPERTY, "custom.xml");
        assertTrue(DevLogging.isConfigurationName("custom.xml"));
        System.setProperty(PROPERTY, "config/custom.xml");
        assertTrue(DevLogging.isConfigurationName("custom.xml"));
        System.setProperty(PROPERTY, "config\\custom.xml");
        assertTrue(DevLogging.isConfigurationName("custom.xml"));
        assertFalse(DevLogging.isConfigurationName("other.xml"));
    }

    @Test
    void aChangeConcernsLogbackWhenAFileOrResourceHasTheNameOfItsConfiguration() {
        System.clearProperty(PROPERTY);
        assertTrue(DevLogging.anyConfigurationFile(List.of(Path.of("src/main/resources/app.txt"), Path.of("src/main/resources/logback.xml"))));
        assertFalse(DevLogging.anyConfigurationFile(List.of(Path.of("src/main/resources/app.txt"), Path.of("/"))));
        assertTrue(DevLogging.anyConfigurationName(Set.of("logback-test.xml")));
        assertTrue(DevLogging.anyConfigurationName(Set.of("config/logback.xml")));
        assertFalse(DevLogging.anyConfigurationName(Set.of("app/logback.txt")));
    }
}
