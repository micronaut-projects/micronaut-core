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

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonConfigurationTest {

    @Test
    void pythonIsEnabledByDefault() {
        try (ApplicationContext context = ApplicationContext.run()) {
            assertTrue(context.containsBean(Engine.class, Qualifiers.byName(PYTHON)));
        }
    }

    @Test
    void pythonCanBeDisabledWithApplicationConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(PythonConfiguration.ENABLED_PROPERTY, false))) {
            assertFalse(context.containsBean(Engine.class, Qualifiers.byName(PYTHON)));
            assertFalse(context.containsBean(Context.class, Qualifiers.byName(PYTHON)));
            assertFalse(context.containsBean(HostAccess.class, Qualifiers.byName(PYTHON)));
        }
    }

    @Test
    void pythonCanBeDisabledWithSystemProperty() {
        String previous = System.getProperty(PythonConfiguration.ENABLED_PROPERTY);
        try {
            System.setProperty(PythonConfiguration.ENABLED_PROPERTY, "false");
            try (ApplicationContext context = ApplicationContext.run()) {
                assertFalse(context.containsBean(Engine.class, Qualifiers.byName(PYTHON)));
            }
        } finally {
            if (previous == null) {
                System.clearProperty(PythonConfiguration.ENABLED_PROPERTY);
            } else {
                System.setProperty(PythonConfiguration.ENABLED_PROPERTY, previous);
            }
        }
    }
}
