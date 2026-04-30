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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Deterministic unit test for {@link RefreshScope#getOrCreate(BeanCreationContext)}.
 * <p>
 * Verifies that nested {@code getOrCreate()} calls do not throw
 * {@code IllegalStateException: Recursive update}, which was previously caused
 * by {@code ConcurrentHashMap.computeIfAbsent()} when two {@link BeanIdentifier}
 * keys hashed to the same bucket.
 */
class RefreshScopeGetOrCreateTest {

    @Test
    void nestedGetOrCreateWithCollidingHashCodesDoesNotThrow() {
        var refreshScope = new RefreshScope(null);

        // Force both identifiers to the same hashCode to guarantee a bucket collision
        var idB = new StubBeanIdentifier("B", 42);
        var contextB = new StubBeanCreationContext<>(idB, "beanB");

        var idA = new StubBeanIdentifier("A", 42);
        // contextA's create() triggers a nested getOrCreate() for contextB,
        // simulating a @Refreshable bean whose @PostConstruct resolves
        // another @Refreshable dependency.
        var contextA = new StubBeanCreationContext<>(idA, "beanA") {
            @Override
            public CreatedBean<String> create() {
                refreshScope.getOrCreate(contextB);
                return super.create();
            }
        };

        String resultA = assertDoesNotThrow(() -> refreshScope.getOrCreate(contextA));
        assertEquals("beanA", resultA);
        assertEquals("beanB", refreshScope.getOrCreate(contextB));
    }

    /**
     * A {@link BeanIdentifier} with a controllable {@code hashCode()},
     * allowing forced bucket collisions in {@code ConcurrentHashMap}.
     */
    static class StubBeanIdentifier implements BeanIdentifier {
        private final String name;
        private final int hash;

        StubBeanIdentifier(String name, int hash) {
            this.name = name;
            this.hash = hash;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return name.subSequence(start, end);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StubBeanIdentifier that)) {
                return false;
            }
            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    static class StubBeanCreationContext<T> implements BeanCreationContext<T> {
        private final BeanIdentifier id;
        private final T bean;

        StubBeanCreationContext(BeanIdentifier id, T bean) {
            this.id = id;
            this.bean = bean;
        }

        @Override
        public BeanDefinition<T> definition() {
            return null;
        }

        @Override
        public BeanIdentifier id() {
            return id;
        }

        @Override
        public CreatedBean<T> create() {
            return new StubCreatedBean<>(id, bean);
        }
    }

    static class StubCreatedBean<T> implements CreatedBean<T> {
        private final BeanIdentifier id;
        private final T bean;

        StubCreatedBean(BeanIdentifier id, T bean) {
            this.id = id;
            this.bean = bean;
        }

        @Override
        public BeanDefinition<T> definition() {
            return null;
        }

        @Override
        public T bean() {
            return bean;
        }

        @Override
        public BeanIdentifier id() {
            return id;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
