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
package example.micronaut.synthesis;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.beans.BeanIntrospection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that {@link AnnotationMetadata#synthesize(Class)} can hand out real annotation instances in
 * a native image when the annotation type carries a {@code DYNAMIC_PROXY} reflection hint.
 *
 * <p>{@code synthesize(..)} builds the instance as a JDK dynamic proxy of the annotation type
 * <em>and</em> {@link AnnotationValueProvider}. Before the fix, the hint registered a proxy of the
 * annotation type alone, so every test here failed in a native image with
 * {@code MissingReflectionRegistrationError: Cannot reflectively access the proxy class inheriting
 * ['example.micronaut.synthesis.Guarded','io.micronaut.core.annotation.AnnotationValueProvider']}.
 * They all pass on the JVM either way, so only the {@code nativeTest} run is a regression test.</p>
 */
class SynthesizeInNativeImageTest {

    private static AnnotationMetadata metadata() {
        return BeanIntrospection.getIntrospection(Protected.class).getAnnotationMetadata();
    }

    @Test
    void synthesizesAnAnnotationWithItsMembers() {
        Guarded guarded = metadata().synthesize(Guarded.class);

        assertNotNull(guarded);
        assertEquals("secret", guarded.value());
        assertEquals(3, guarded.max());
        assertEquals(Guarded.class, guarded.annotationType());
    }

    @Test
    void theSynthesizedInstanceIsAlsoAnAnnotationValueProvider() {
        // this is the second interface of the proxy, and the reason registering the annotation type on
        // its own is not enough
        Guarded guarded = metadata().synthesize(Guarded.class);

        AnnotationValueProvider<?> provider = assertInstanceOf(AnnotationValueProvider.class, guarded);
        assertEquals("secret", provider.annotationValue().stringValue().orElse(null));
    }

    @Test
    void synthesizesARepeatableAnnotationThroughItsContainer() {
        Role[] roles = metadata().synthesizeAnnotationsByType(Role.class);

        assertEquals(2, roles.length);
        assertArrayEquals(new String[]{"admin", "auditor"}, new String[]{roles[0].value(), roles[1].value()});
    }

    @Test
    void synthesizesTheRepeatableContainerItself() {
        Roles roles = metadata().synthesize(Roles.class);

        assertNotNull(roles);
        assertEquals(2, roles.value().length);
    }
}
