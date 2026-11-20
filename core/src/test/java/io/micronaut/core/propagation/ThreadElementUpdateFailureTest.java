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
package io.micronaut.core.propagation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A thread element failing to update or restore the thread must not leave the context, or the state of
 * the other elements, on the thread.
 */
class ThreadElementUpdateFailureTest {

    private static final ThreadLocal<String> STATE = new ThreadLocal<>();

    @BeforeEach
    void setup() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
        STATE.remove();
    }

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
        STATE.remove();
    }

    @Test
    void failingElementWithNoOuterContext() {
        PropagatedContext context = PropagatedContext.empty()
            .plus(new StateElement("inner"))
            .plus(new FailingElement());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.propagate(() -> {
            throw new AssertionError("Must not run");
        }));

        assertEquals("update failed", e.getMessage());
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(STATE.get());
    }

    @Test
    void failingElementInsideAnotherContext() {
        PropagatedContext outer = PropagatedContext.empty().plus(new StateElement("outer"));
        PropagatedContext inner = outer.plus(new StateElement("inner")).plus(new FailingElement());

        outer.propagate(() -> {
            assertThrows(IllegalStateException.class, () -> inner.propagate(() -> {
                throw new AssertionError("Must not run");
            }));
            assertSame(outer, PropagatedContext.get());
            assertEquals("outer", STATE.get());
        });
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(STATE.get());
    }

    @Test
    void singleFailingElement() {
        PropagatedContext context = PropagatedContext.empty().plus(new FailingElement());

        assertThrows(IllegalStateException.class, () -> context.propagate(() -> {
            throw new AssertionError("Must not run");
        }));

        assertFalse(PropagatedContext.find().isPresent());
    }

    @Test
    void failingRestoreStillRestoresTheOtherElementsAndTheContext() {
        PropagatedContext outer = PropagatedContext.empty().plus(new StateElement("outer"));
        PropagatedContext inner = outer.plus(new StateElement("inner")).plus(new FailingRestoreElement());

        outer.propagate(() -> {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> inner.propagate(() -> {
                assertEquals("inner", STATE.get());
            }));
            assertEquals("restore failed", e.getMessage());
            assertSame(outer, PropagatedContext.get());
            assertEquals("outer", STATE.get());
        });
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(STATE.get());
    }

    @Test
    void singleFailingRestoreStillRestoresTheContext() {
        PropagatedContext outer = PropagatedContext.empty().plus(new StateElement("outer"));
        PropagatedContext inner = PropagatedContext.empty().plus(new FailingRestoreElement());

        outer.propagate(() -> {
            assertThrows(IllegalStateException.class, () -> inner.propagate(() -> {
            }));
            assertSame(outer, PropagatedContext.get());
        });
        assertFalse(PropagatedContext.find().isPresent());
    }

    @Test
    void updateFailureWinsOverRollbackFailure() {
        PropagatedContext context = PropagatedContext.empty()
            .plus(new StateElement("first"))
            .plus(new FailingRestoreElement())
            .plus(new FailingElement());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.propagate(() -> {
            throw new AssertionError("Must not run");
        }));

        assertEquals("update failed", e.getMessage());
        assertEquals(1, e.getSuppressed().length);
        assertEquals("restore failed", e.getSuppressed()[0].getMessage());
        assertFalse(PropagatedContext.find().isPresent());
        // the element before the failing rollback is still restored
        assertNull(STATE.get());
    }

    @Test
    void sameRestoreFailureTwice() {
        IllegalStateException cached = new IllegalStateException("cached");
        FailingRestoreElement element = new FailingRestoreElement(cached);
        PropagatedContext context = PropagatedContext.empty()
            .plus(new StateElement("first"))
            .plus(element)
            .plus(element);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> context.propagate(() -> {
            assertEquals("first", STATE.get());
        }));

        assertSame(cached, e);
        assertEquals(0, e.getSuppressed().length);
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(STATE.get());
    }

    private static final class FailingRestoreElement implements ThreadPropagatedContextElement<Object> {

        private final RuntimeException failure;

        FailingRestoreElement() {
            this(new IllegalStateException("restore failed"));
        }

        FailingRestoreElement(RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Object updateThreadContext() {
            return null;
        }

        @Override
        public void restoreThreadContext(Object oldState) {
            throw failure;
        }
    }

    private record StateElement(String value) implements ThreadPropagatedContextElement<String> {

        @Override
        public String updateThreadContext() {
            String old = STATE.get();
            STATE.set(value);
            return old;
        }

        @Override
        public void restoreThreadContext(String oldState) {
            if (oldState == null) {
                STATE.remove();
            } else {
                STATE.set(oldState);
            }
        }
    }

    private static final class FailingElement implements ThreadPropagatedContextElement<Object> {

        @Override
        public Object updateThreadContext() {
            throw new IllegalStateException("update failed");
        }

        @Override
        public void restoreThreadContext(Object oldState) {
            throw new AssertionError("Not updated, must not be restored");
        }
    }
}
