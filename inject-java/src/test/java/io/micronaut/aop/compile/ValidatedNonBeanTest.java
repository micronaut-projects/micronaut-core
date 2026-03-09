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
package io.micronaut.aop.compile;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Java port of ValidatedNonBeanSpec.
 */
class ValidatedNonBeanTest extends AbstractTypeElementTest {

    @Test
    void testClassWithOnlyValidationAnnotationIsNotBean() {
        BeanDefinition<?> beanDefinition = buildBeanDefinition("test.DefaultContract", """
            package test;

            import jakarta.validation.constraints.NotNull;
            import io.micronaut.context.annotation.*;
            import jakarta.inject.Singleton;

            class DefaultContract implements Contract {

                public Long parseLong(@NotNull CharSequence sequence) {
                    return 0L;
                }
            }

            interface Contract {
                Long parseLong(@NotNull CharSequence sequence);
            }
            """);
        assertNull(beanDefinition);
    }
}
