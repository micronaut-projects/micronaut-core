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
package io.micronaut.context;

import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateCacheRaceTest {

    @Test
    void aLookupThatRacedARegistrationDoesNotKeepItsAnswerCached() {
        AtomicBoolean armed = new AtomicBoolean();
        // runs after the lookup collected its candidates and before it caches the answer: the registration of a
        // prototype definition lands in that window, as one from another thread may
        BeanResolutionCustomizer registeringCustomizer = new BeanResolutionCustomizer() {
            @Override
            public BeanResolutionCustomizer forContext(BeanContext beanContext) {
                return new BeanResolutionCustomizer() {
                    @Override
                    public Argument<?> resolveBeanLookupArgument(Argument<?> beanType) {
                        if (beanType.getType() == Raced.class && armed.compareAndSet(true, false)) {
                            ((BeanDefinitionRegistry) beanContext).registerBeanDefinition(
                                RuntimeBeanDefinition.builder(Raced.class, Raced::new).qualifier(Qualifiers.byName("raced")).build());
                        }
                        return beanType;
                    }
                };
            }
        };
        try (ApplicationContext context = ApplicationContext.builder().beanResolutionCustomizer(registeringCustomizer).start()) {
            armed.set(true);

            // the lookup answers with what it saw
            assertFalse(context.findBean(Raced.class, Qualifiers.byName("raced")).isPresent());
            // the next lookup sees the registered bean rather than a cached absence
            assertTrue(context.findBean(Raced.class, Qualifiers.byName("raced")).isPresent());
        }
    }

    static final class Raced {
    }
}
