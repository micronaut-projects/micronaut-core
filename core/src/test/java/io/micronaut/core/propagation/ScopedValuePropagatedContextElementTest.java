package io.micronaut.core.propagation;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.ScopedValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopedValuePropagatedContextElementTest {

    private static final ScopedValue<String> FIRST = ScopedValue.newInstance();
    private static final ScopedValue<String> SECOND = ScopedValue.newInstance();
    private static final ScopedValue<String> HYBRID = ScopedValue.newInstance();
    private static final ThreadLocal<String> HYBRID_THREAD_LOCAL = new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicInteger HYBRID_THREAD_UPDATES = new java.util.concurrent.atomic.AtomicInteger();

    @BeforeEach
    void setUp() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
    }

    @AfterEach
    void tearDown() {
        PropagatedContextConfiguration.reset();
    }

    @Test
    void bindsScopedValueElements() {
        assertFalse(FIRST.isBound());

        PropagatedContext context = PropagatedContext.getOrEmpty()
            .plus(new ScopedValueElement(FIRST, "first-value"));

        String result = context.propagate(() -> {
            assertTrue(FIRST.isBound());
            return FIRST.get();
        });

        assertEquals("first-value", result);
        assertFalse(FIRST.isBound());
    }

    @Test
    void bindsMultipleScopedValueElements() {
        PropagatedContext context = PropagatedContext.getOrEmpty()
            .plus(new ScopedValueElement(FIRST, "first"))
            .plus(new ScopedValueElement(SECOND, "second"));

        context.propagate(() -> {
            assertEquals("first", FIRST.get());
            assertEquals("second", SECOND.get());
        });

        assertFalse(FIRST.isBound());
        assertFalse(SECOND.isBound());
    }

    @Test
    void allowsNullValues() {
        PropagatedContext context = PropagatedContext.getOrEmpty()
            .plus(new ScopedValueElement(FIRST, null));

        context.propagate(() -> assertNull(FIRST.get()));
    }

    @Test
    void scopedValueThreadLocalHybridUsesScopedValueWhenAvailable() {
        HYBRID_THREAD_LOCAL.remove();
        HYBRID_THREAD_UPDATES.set(0);

        HybridElement element = new HybridElement("hybrid-value");
        PropagatedContext context = PropagatedContext.getOrEmpty().plus(element);

        String resolved = context.propagate(element::currentValue);

        assertEquals("hybrid-value", resolved);
        assertEquals(0, HYBRID_THREAD_UPDATES.get());
        assertNull(HYBRID_THREAD_LOCAL.get());
    }

    @Test
    void scopedValueThreadLocalHybridFallsBackToThreadLocal() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
        HYBRID_THREAD_LOCAL.remove();
        HYBRID_THREAD_UPDATES.set(0);

        HybridElement element = new HybridElement("thread-local");
        PropagatedContext context = PropagatedContext.getOrEmpty().plus(element);

        String resolved = context.propagate(element::currentValue);

        assertEquals("thread-local", resolved);
        assertEquals(1, HYBRID_THREAD_UPDATES.get());
        assertNull(HYBRID_THREAD_LOCAL.get());
    }

    private record ScopedValueElement(ScopedValue<String> scopedValue, String scopedValueValue)
        implements ScopedValuePropagatedContextElement<String> {
    }

    private record HybridElement(String value) implements ScopedValuePropagatedContextElement<String>, ThreadPropagatedContextElement<String> {

        @Override
        public ScopedValue<String> scopedValue() {
            return HYBRID;
        }

        @Override
        public String scopedValueValue() {
            return value;
        }

        String currentValue() {
            if (HYBRID.isBound()) {
                return HYBRID.get();
            }
            return HYBRID_THREAD_LOCAL.get();
        }

        @Override
        public @Nullable String updateThreadContext() {
            if (HYBRID.isBound()) {
                return null;
            }
            HYBRID_THREAD_UPDATES.incrementAndGet();
            String previous = HYBRID_THREAD_LOCAL.get();
            HYBRID_THREAD_LOCAL.set(value);
            return previous;
        }

        @Override
        public void restoreThreadContext(@Nullable String state) {
            if (state == null && HYBRID.isBound()) {
                return;
            }
            if (state == null) {
                HYBRID_THREAD_LOCAL.remove();
            } else {
                HYBRID_THREAD_LOCAL.set(state);
            }
        }
    }
}
