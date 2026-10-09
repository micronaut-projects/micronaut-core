package io.micronaut.core.propagation;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropagatedContextScopeSupportTest {

    private static final ThreadLocal<String> THREAD_STATE = new ThreadLocal<>();

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
        THREAD_STATE.remove();
    }

    @Test
    void threadLocalModeSupportsScopes() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
        assertTrue(PropagatedContext.supportsScopes());

        PropagatedContext context = PropagatedContext.getOrEmpty().plus(new Element("a"));
        PropagatedContext.Scope scope = context.propagateIfSupported();
        assertNotNull(scope);
        try {
            assertSame(context, PropagatedContext.get());
            assertEquals("a", THREAD_STATE.get());
        } finally {
            scope.close();
        }
        assertFalse(PropagatedContext.exists());
        assertNull(THREAD_STATE.get());
    }

    @Test
    void threadLocalModeRestoresThePreviousContext() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
        PropagatedContext outer = PropagatedContext.getOrEmpty().plus(new Element("outer"));
        PropagatedContext inner = outer.plus(new Element("inner"));

        outer.propagate(() -> {
            PropagatedContext.Scope scope = inner.propagateIfSupported();
            assertNotNull(scope);
            assertSame(inner, PropagatedContext.get());
            assertEquals("inner", THREAD_STATE.get());
            scope.close();
            assertSame(outer, PropagatedContext.get());
            assertEquals("outer", THREAD_STATE.get());
        });
    }

    @Test
    void scopedValueModeDoesNotSupportScopes() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
        assertFalse(PropagatedContext.supportsScopes());

        PropagatedContext context = PropagatedContext.getOrEmpty().plus(new Element("a"));
        assertNull(context.propagateIfSupported());
        assertFalse(PropagatedContext.exists());
        assertNull(THREAD_STATE.get());

        // The deprecated method keeps failing
        @SuppressWarnings("deprecation")
        IllegalStateException e = assertThrows(IllegalStateException.class, context::propagate);
        assertTrue(e.getMessage().contains("thread-local"));
    }

    @Test
    void scopedValueModeLeavesTheCallbackContextUntouched() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
        PropagatedContext outer = PropagatedContext.getOrEmpty().plus(new Element("outer"));

        outer.propagate(() -> {
            assertNull(outer.plus(new Element("inner")).propagateIfSupported());
            assertSame(outer, PropagatedContext.get());
            assertEquals("outer", THREAD_STATE.get());
        });
    }

    @Test
    void iteratorStyleIntegrationWithThreadLocalPropagation() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
        List<@Nullable String> seen = consume(new ContextIterator(List.of("a", "b", "c")));
        assertEquals(List.of("a", "b", "c"), seen);
        assertFalse(PropagatedContext.exists());
        assertNull(THREAD_STATE.get());
    }

    @Test
    void iteratorStyleIntegrationWithScopedValuePropagation() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
        List<@Nullable String> seen = consume(new ContextIterator(List.of("a", "b", "c")));
        assertEquals(Arrays.asList(null, null, null), seen);
        assertFalse(PropagatedContext.exists());
    }

    /**
     * Mimics user code iterating over records: the context of the current record must be in scope between calls to
     * {@code next()}.
     */
    private static List<@Nullable String> consume(ContextIterator iterator) {
        List<@Nullable String> seen = new ArrayList<>();
        try (iterator) {
            while (iterator.hasNext()) {
                iterator.next();
                seen.add(PropagatedContext.find().map(ctx -> ctx.get(Element.class).value).orElse(null));
            }
        }
        return seen;
    }

    /**
     * An iterator that brings the context of each element into scope when it is returned by {@code next()} and takes
     * it out of scope on the following {@code next()} or {@code close()}.
     */
    private static final class ContextIterator implements Iterator<String>, AutoCloseable {

        private final Iterator<String> delegate;
        private PropagatedContext.@Nullable Scope scope;

        ContextIterator(List<String> values) {
            this.delegate = values.iterator();
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public String next() {
            closeScope();
            String value = delegate.next();
            scope = PropagatedContext.getOrEmpty().plus(new Element(value)).propagateIfSupported();
            return value;
        }

        @Override
        public void close() {
            closeScope();
        }

        private void closeScope() {
            if (scope != null) {
                scope.close();
                scope = null;
            }
        }
    }

    private record Element(String value) implements ThreadPropagatedContextElement<@Nullable String> {

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
}
