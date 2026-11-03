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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DevLoggingCompilationTest {

    private final Logger environment = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(DevLogging.ENVIRONMENT_LOGGER);

    @AfterEach
    void restore() {
        environment.setLevel(null);
    }

    @Test
    void theEnvironmentsOfACompilationDoNotReportTheActiveEnvironments() {
        environment.setLevel(null);
        try (DevLogging.Quiet quiet = DevLogging.quietCompilation()) {
            assertEquals(Level.WARN, environment.getEffectiveLevel());
        }
        // the application's environment reports them again
        assertNull(environment.getLevel());
    }

    @Test
    void aLevelTheApplicationConfiguredApplies() {
        environment.setLevel(Level.DEBUG);
        try (DevLogging.Quiet quiet = DevLogging.quietCompilation()) {
            assertEquals(Level.DEBUG, environment.getLevel());
        }
        assertEquals(Level.DEBUG, environment.getLevel());
    }

    @Test
    void aLevelTheApplicationConfiguredForAPackageApplies() {
        Logger pkg = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("io.micronaut.context.env");
        environment.setLevel(null);
        pkg.setLevel(Level.INFO);
        try (DevLogging.Quiet quiet = DevLogging.quietCompilation()) {
            assertEquals(Level.INFO, environment.getEffectiveLevel());
        } finally {
            pkg.setLevel(null);
        }
    }
}
