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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class GraalPyEngineFactoryTest {

    @Test
    void configuredEngineOptionsWinOverBuiltInDefaults() {
        GraalPyEngineFactory.GraalPyEngineConfiguration configuration = new GraalPyEngineFactory.GraalPyEngineConfiguration();
        assertEquals("1", configuration.optionalOptions.get("engine.CompilerThreads"));

        configuration.setOptions(Map.of("engine.CompilerThreads", "4"));

        assertFalse(GraalPyEngineFactory.optionalOptionsToApply(configuration).containsKey("engine.CompilerThreads"),
            "the built-in CompilerThreads default must not override the configured value");
    }

    @Test
    void engineOptionsCanBeConfiguredFromMicronautProperties() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "graalpy.engine.allow-experimental-options", true,
            // an option both the optimizing and the fallback runtime know
            "graalpy.engine.options", Map.of("engine.WarnInterpreterOnly", "false")
        ))) {
            Context context = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));

            assertEquals(1, context.eval(PYTHON, "1").asInt());
        }
    }
}
