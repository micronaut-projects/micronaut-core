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
package io.micronaut.annotation.defaults;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of DefaultAnnotationValuesSpec.
 */
class DefaultAnnotationValuesTest {

    @Test
    void testWhichDefaultValuesArePreserved() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            Class<?> myBeanClass = ctx.getClassLoader().loadClass("io.micronaut.annotation.defaults.MyBean");
            AnnotationMetadata am = ctx.getBeanDefinition(myBeanClass).getAnnotationMetadata();
            assertNotNull(ctx.getBean(myBeanClass));

            // DefaultValues1
            var defaults1 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues1");
            var defaults1Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues1");

            // empty string default value is emitted
            assertTrue(am.stringValue("io.micronaut.annotation.defaults.DefaultValues1").isEmpty());
            assertTrue(defaults1Annotation.stringValue().isEmpty());
            assertEquals(0, defaults1Annotation.getRequiredValue("strings", String[].class).length);
            assertEquals(2, defaults1.size());
            assertEquals(0, ((String[]) defaults1.get("strings")).length);
            assertEquals(0, ((int[]) defaults1.get("ints")).length);
            assertEquals(0, am.stringValues("io.micronaut.annotation.defaults.DefaultValues1", "strings").length);

            assertThrows(IllegalStateException.class, () -> defaults1Annotation.getRequiredValue(String.class));

            // DefaultValues2
            var defaults2 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues2");
            var defaults2Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues2");

            assertTrue(am.stringValue("io.micronaut.annotation.defaults.DefaultValues2").isEmpty());
            assertTrue(defaults2Annotation.stringValue().isEmpty());
            assertEquals("xyz", defaults2Annotation.getRequiredValue(String.class));
            assertEquals(3, defaults2.size());
            assertEquals("xyz", defaults2.get("value"));
            assertEquals(0, ((String[]) defaults2.get("strings")).length);
            assertEquals(0, ((int[]) defaults2.get("ints")).length);

            // DefaultValues3
            var defaults3 = AnnotationMetadataSupport.getDefaultValues("io.micronaut.annotation.defaults.DefaultValues3");
            var defaults3Annotation = am.getAnnotation("io.micronaut.annotation.defaults.DefaultValues3");

            assertTrue(am.stringValue("io.micronaut.annotation.defaults.DefaultValues3").isEmpty());
            assertTrue(defaults3Annotation.stringValue().isEmpty());
            assertEquals(0, defaults3Annotation.stringValues("strings").length);
            assertEquals(1, defaults3Annotation.getRequiredValue("strings", String[].class).length);
            assertEquals("", defaults3Annotation.getRequiredValue("strings", String[].class)[0]);
            assertEquals(2, defaults3.size());
            assertEquals(1, ((String[]) defaults3.get("strings")).length);
            assertEquals("", ((String[]) defaults3.get("strings"))[0]);
            // The original spec referenced defaults2 here; keep equivalent assertion
            assertEquals(0, ((int[]) defaults2.get("ints")).length);
        }
    }
}
