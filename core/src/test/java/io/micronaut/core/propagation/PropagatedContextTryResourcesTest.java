package io.micronaut.core.propagation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class PropagatedContextTryResourcesTest {

    @Test
    void testEmpty() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                    try (PropagatedContext.Scope ignore4 = PropagatedContext.empty().propagate()) {
                        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
                        assertIterableEquals(Collections.emptyList(), propagatedContext.getAllElements());
                        assertFalse(PropagatedContext.exists());
                    }
                }
            }
        }
    }

    @Test
    void testMinus() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                    PropagatedContext propagatedContext = PropagatedContext.get();
                    assertEquals(List.of(e1, e2, e3), propagatedContext.getAllElements());
                    assertEquals(List.of(e2, e3), propagatedContext.minus(e1).getAllElements());
                    assertEquals(List.of(e1, e3), propagatedContext.minus(e2).getAllElements());
                    assertEquals(List.of(e1, e2), propagatedContext.minus(e3).getAllElements());
                    assertEquals(List.of(e3), propagatedContext.minus(e1).minus(e2).getAllElements());
                    assertEquals(List.of(), propagatedContext.minus(e1).minus(e2).minus(e3).getAllElements());
                }
            }
        }
    }

    @Test
    void testMultipleElementsOfTheSameType() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                    PropagatedContext propagatedContext = PropagatedContext.get();
                    assertEquals(e3, propagatedContext.get(PropagatedElement.class));
                    assertEquals(e3, propagatedContext.find(PropagatedElement.class).get());
                    assertTrue(propagatedContext.getAllElements().containsAll(List.of(e1, e2, e3)));
                    assertEquals(List.of(e3, e2, e1), propagatedContext.findAll(PropagatedElement.class).toList());
                }
            }
        }
    }

    @Test
    void testRemovingElement() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
        try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().minus(e2).propagate()) {
                        assertEquals(List.of(e3, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    }
                    try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().minus(e2).minus(e1).propagate()) {
                        assertEquals(List.of(e3), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    }
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                }
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            }
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
        }
        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
    }

    @Test
    void testReplacingElement() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();
        PropagatedElement e4 = new PropagatedElement();

        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
        try (PropagatedContext.Scope ignore1 = PropagatedContext.getOrEmpty().plus(e1).propagate()) {
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().plus(e2).propagate()) {
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                try (PropagatedContext.Scope ignore3 = PropagatedContext.getOrEmpty().plus(e3).propagate()) {
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    try (PropagatedContext.Scope ignore4 = PropagatedContext.getOrEmpty().replace(e2, e4).propagate()) {
                        assertEquals(List.of(e3, e4, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    }
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                }
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            }
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
        }
        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
    }

    @Test
    void testRemovingMissingElement() {
        PropagatedElement e1 = new PropagatedElement();
        assertThrows(NoSuchElementException.class, () -> {
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().minus(e1).propagate()) {
                // no-op
            }
        });
    }

    @Test
    void testReplacingMissingElement() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        assertThrows(NoSuchElementException.class, () -> {
            try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().replace(e1, e2).propagate()) {
                // no-op
            }
        });
    }

    @Test
    void testEmptyPropagatedContextCleansTheContext() {
        PropagatedElement e1 = new PropagatedElement();

        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().propagate()) {
            PropagatedContext.getOrEmpty().plus(e1).propagate();
            assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
        }
        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
    }

    @Test
    void testEmptyPropagatedContextRestoresTheContext() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e = new PropagatedElement();

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty().plus(e).propagate()) {
            assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
            try (PropagatedContext.Scope ignore2 = PropagatedContext.getOrEmpty().propagate()) {
                PropagatedContext.getOrEmpty().plus(e1).propagate();
                assertEquals(2, PropagatedContext.getOrEmpty().getAllElements().size());
            }
            assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
        }
        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRestoringThreadContext(boolean strict) {
        String before = "";
        String inside;
        String after;

        CONTEXT_NAME_HOLDER.set(before);
        PropagatedContext context = PropagatedContext.empty()
            .plus(new SetContextName("one", strict))
            .plus(new SetContextName("two", strict));

        try (var ignore = context.propagate()) {
            inside = getCurrentContextName();
        }
        after = getCurrentContextName();

        assertEquals("two", inside);
        assertEquals(before, after);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRestoringThreadContextInAChain(boolean strict) {
        String before = "";
        String firstIn;
        String firstOut;
        String secondIn;
        String secondOut;

        CONTEXT_NAME_HOLDER.set(before);

        try (var ignore1 = PropagatedContext.empty().plus(new SetContextName("first", strict)).propagate()) {
            firstIn = getCurrentContextName();
            try (var ignore2 = PropagatedContext.get().plus(new SetContextName("second", strict)).propagate()) {
                secondIn = getCurrentContextName();
            }
            secondOut = getCurrentContextName();
        }
        firstOut = getCurrentContextName();

        assertEquals("first", firstIn);
        assertEquals("second", secondIn);
        assertEquals("first", secondOut);
        assertEquals(before, firstOut);
    }

    static class PropagatedElement implements PropagatedContextElement {
    }

    static final ThreadLocal<String> CONTEXT_NAME_HOLDER = ThreadLocal.withInitial(() -> "");

    static String getCurrentContextName() {
        return CONTEXT_NAME_HOLDER.get();
    }

    record SetContextName(String name,
                          boolean strict) implements ThreadPropagatedContextElement<String> {

        @Override
        public String updateThreadContext() {
            String current = getCurrentContextName();
            CONTEXT_NAME_HOLDER.set(name);
            return current;
        }

        @Override
        public void restoreThreadContext(String oldState) {
            String current = getCurrentContextName();
            if (Objects.equals(name, current)) {
                CONTEXT_NAME_HOLDER.set(oldState);
            } else {
                handleIncorrectContext(current);
            }
        }

        private void handleIncorrectContext(String currentContextName) {
            if (strict) {
                throw new IllegalStateException("Expected to be in context: " + name + " but was: " + currentContextName);
            }
            // otherwise: ignore silently
        }
    }

}
