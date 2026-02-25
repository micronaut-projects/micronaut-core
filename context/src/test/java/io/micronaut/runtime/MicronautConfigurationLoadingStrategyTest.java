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
package io.micronaut.runtime;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MicronautConfigurationLoadingStrategyTest {

    @Test
    void configurationLoadingStrategyIsFluent() {
        Micronaut micronaut = Micronaut.build(new String[0])
            .configurationLoadingStrategy(ResourceLoadStrategy.builder()
                .type(ResourceLoadStrategyType.FIRST_MATCH)
                .warnOnDuplicates(false));

        try (ApplicationContext ctx = micronaut.start()) {
            assertNotNull(ctx);
        }
    }
}
