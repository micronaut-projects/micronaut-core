package io.micronaut.core.propagation;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreadBoundPropagationTest {

    private static final ThreadLocal<String> THREAD_STATE = new ThreadLocal<>();
    private static final ScopedValue<String> SCOPED = ScopedValue.newInstance();

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
        THREAD_STATE.remove();
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void bindsTheContextUntilTheScopeIsClosed(PropagatedContextConfiguration.Mode mode) {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext context = PropagatedContext.empty().plus(new Element("a"));

        PropagatedContext.Scope scope = ThreadBoundPropagation.bind(context);
        assertSame(context, PropagatedContext.get());
        assertTrue(context.isBound());
        assertEquals("a", THREAD_STATE.get());

        scope.close();
        assertFalse(PropagatedContext.exists());
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(THREAD_STATE.get());
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void restoresThePreviousBinding(PropagatedContextConfiguration.Mode mode) {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext first = PropagatedContext.empty().plus(new Element("first"));
        PropagatedContext second = PropagatedContext.empty().plus(new Element("second"));

        try (PropagatedContext.Scope ignored = ThreadBoundPropagation.bind(first)) {
            try (PropagatedContext.Scope ignored2 = ThreadBoundPropagation.bind(second)) {
                assertSame(second, PropagatedContext.get());
                assertEquals("second", THREAD_STATE.get());
            }
            assertSame(first, PropagatedContext.get());
            assertEquals("first", THREAD_STATE.get());
        }
        assertFalse(PropagatedContext.find().isPresent());
        assertNull(THREAD_STATE.get());
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void takesPrecedenceOverTheContextItIsBoundIn(PropagatedContextConfiguration.Mode mode) {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext outer = PropagatedContext.empty().plus(new Element("outer"));
        PropagatedContext bound = PropagatedContext.empty().plus(new Element("bound"));

        outer.propagate(() -> {
            try (PropagatedContext.Scope ignored = ThreadBoundPropagation.bind(bound)) {
                assertSame(bound, PropagatedContext.get());
                assertEquals("bound", THREAD_STATE.get());
            }
            assertSame(outer, PropagatedContext.get());
            assertEquals("outer", THREAD_STATE.get());
        });
        assertFalse(PropagatedContext.find().isPresent());
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void aContextPropagatedWithACallbackTakesPrecedenceForItsExtent(PropagatedContextConfiguration.Mode mode) {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext bound = PropagatedContext.empty().plus(new Element("bound"));
        PropagatedContext nested = bound.plus(new Element("nested"));

        try (PropagatedContext.Scope ignored = ThreadBoundPropagation.bind(bound)) {
            nested.propagate(() -> {
                assertSame(nested, PropagatedContext.get());
                assertEquals("nested", THREAD_STATE.get());
                PropagatedContext.empty().propagate(() -> assertTrue(PropagatedContext.getOrEmpty().isEmpty()));
            });
            assertSame(bound, PropagatedContext.get());
            assertEquals("bound", THREAD_STATE.get());
            // Already in scope, runs directly
            assertSame(bound, bound.propagate((Supplier<PropagatedContext>) PropagatedContext::get));
        }
        assertFalse(PropagatedContext.find().isPresent());
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void aWrappedTaskSeesTheBoundContextOnAnotherThread(PropagatedContextConfiguration.Mode mode) throws Exception {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext bound = PropagatedContext.empty().plus(new Element("bound"));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Runnable check;
            try (PropagatedContext.Scope ignored = ThreadBoundPropagation.bind(bound)) {
                check = PropagatedContext.wrapCurrent(() -> {
                    assertSame(bound, PropagatedContext.get());
                    assertEquals("bound", THREAD_STATE.get());
                });
            }
            executor.submit(check).get();
            assertNull(executor.submit(THREAD_STATE::get).get());
            assertFalse(executor.submit(() -> PropagatedContext.find().isPresent()).get());
        } finally {
            executor.shutdown();
        }
    }

    @ParameterizedTest
    @EnumSource(PropagatedContextConfiguration.Mode.class)
    void bindsTheScopedValueElementsWhenPropagatedWithACallback(PropagatedContextConfiguration.Mode mode) {
        PropagatedContextConfiguration.set(mode);
        PropagatedContext bound = PropagatedContext.empty().plus(new ScopedElement("value"));

        try (PropagatedContext.Scope ignored = ThreadBoundPropagation.bind(bound)) {
            assertSame(bound, PropagatedContext.get());
            if (mode == PropagatedContextConfiguration.Mode.SCOPED_VALUE) {
                // A scoped value can only be bound by a callback
                assertFalse(SCOPED.isBound());
                assertEquals("value", bound.propagate((Supplier<String>) SCOPED::get));
                assertSame(bound, PropagatedContext.get());
            }
        }
        assertFalse(PropagatedContext.find().isPresent());
    }

    private record Element(String value) implements ThreadPropagatedContextElement<String> {

        @Override
        public @Nullable String updateThreadContext() {
            String previous = THREAD_STATE.get();
            THREAD_STATE.set(value);
            return previous;
        }

        @Override
        public void restoreThreadContext(@Nullable String oldState) {
            if (oldState == null) {
                THREAD_STATE.remove();
            } else {
                THREAD_STATE.set(oldState);
            }
        }
    }

    private record ScopedElement(String value) implements ScopedValuePropagatedContextElement<String> {

        @Override
        public ScopedValue<String> scopedValue() {
            return SCOPED;
        }

        @Override
        public String scopedValueValue() {
            return value;
        }
    }
}
