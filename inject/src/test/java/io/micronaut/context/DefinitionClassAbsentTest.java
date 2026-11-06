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

import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Whether a definition failed for an absent class, from the failure a definition compiled before definitions kept a
 * copy holds, and from the copy a definition compiled since holds.
 */
class DefinitionClassAbsentTest {

    private static final class Definition extends AbstractInitializableBeanDefinitionAndReference<Object> {
        Definition(Throwable failure) {
            super(Object.class, null, null, null, null, null, null, Map.of(),
                new PrecalculatedInfo(Optional.empty(), false, false, false, false, false, false, false),
                null, null, failure);
        }

        @Override
        public BeanDefinition<Object> load() {
            return this;
        }

        @Override
        public Object instantiate(BeanResolutionContext resolutionContext, BeanContext context) {
            throw new UnsupportedOperationException();
        }
    }

    private static void assertClassAbsent(boolean expected, Throwable failure) {
        assertEquals(expected, new Definition(failure).isClassAbsent(), () -> "kept as thrown: " + failure);
        Throwable copy = BeanInitializationFailure.detach(failure);
        assertEquals(expected, new Definition(copy).isClassAbsent(), () -> "kept as a copy: " + failure);
    }

    @Test
    void aClassThatCannotBeFoundIsAbsent() {
        assertClassAbsent(true, new NoClassDefFoundError("a/Missing"));
        assertClassAbsent(true, new NoClassDefFoundError("a/Missing").initCause(new ClassNotFoundException("a.Missing")));
        assertClassAbsent(true, new ClassNotFoundException("a.Missing"));
        assertClassAbsent(true, new TypeNotPresentException("a.Missing", null));
    }

    @Test
    void aClassWhoseInitializerFailedIsNotAbsent() {
        assertClassAbsent(false, new NoClassDefFoundError("Could not initialize class a.Erroneous")
            .initCause(new ExceptionInInitializerError("boom")));
    }

    @Test
    void anyOtherFailureIsNotAbsent() {
        assertClassAbsent(false, new IllegalStateException("broken"));
        assertClassAbsent(false, new ExceptionInInitializerError("boom"));
        assertClassAbsent(false, new NoSuchMethodError("a.Missing.method()"));
    }

    @Test
    void aDefinitionThatDidNotFailIsNotAbsent() {
        assertFalse(new Definition(null).isClassAbsent());
    }
}
