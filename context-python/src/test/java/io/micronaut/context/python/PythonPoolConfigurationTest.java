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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PythonPoolConfigurationTest {

    @Test
    void warnWaitDefaultsToTwoSeconds() {
        // the component is nullable, which must not drop its @Bindable default
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.python.pool.size", 3))) {
            assertEquals(Duration.ofSeconds(2), context.getBean(PythonPoolConfiguration.class).warnWait());
        }
        try (ApplicationContext context = ApplicationContext.run()) {
            assertEquals(Duration.ofSeconds(2), context.getBean(PythonPoolConfiguration.class).warnWait());
        }
    }

    @Test
    void warnWaitCanBeTurnedOff() {
        // PythonPool never warns for a threshold that is not positive
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.python.pool.warn-wait", "0s"))) {
            assertEquals(Duration.ZERO, context.getBean(PythonPoolConfiguration.class).warnWait());
        }
    }
}
