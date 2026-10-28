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
package io.micronaut.docs.inject.tracer;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanResolutionTracerProvider;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingTracerSpec {

    @Test
    void testRegisterTracer() {
        // tag::register[]
        TimingTracer tracer = new TimingTracer();
        try (ApplicationContext context = ApplicationContext.builder()
            .properties(Map.of("spec.name", "TimingTracerSpec"))
            .beanResolutionTracer(tracer) // <1>
            .start()) {
            context.getBean(Greeter.class);
        }
        // end::register[]

        assertTrue(tracer.getTimings().containsKey(Greeter.class.getName()));
    }

    @Test
    void testProviderEnabledInDevelopment() {
        TimingTracerProvider provider = new TimingTracerProvider();

        assertTrue(provider.isEnabled(activation(Set.of(Environment.DEVELOPMENT))));
        assertFalse(provider.isEnabled(activation(Set.of("prod"))));
    }

    private static BeanResolutionTracerProvider.Activation activation(Set<String> environmentNames) {
        return new BeanResolutionTracerProvider.Activation() {
            @Override
            public Set<String> getEnvironmentNames() {
                return environmentNames;
            }

            @Override
            public boolean isDiscoveryEnabled() {
                return false;
            }
        };
    }
}
