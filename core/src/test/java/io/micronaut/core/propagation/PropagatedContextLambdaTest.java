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

abstract class PropagatedContextLambdaTest {

    @Test
    void testIsBounded() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();
        var propagatedContext = PropagatedContext.getOrEmpty().plus(e1).plus(e2);
        assertFalse(propagatedContext.isBound());
        propagatedContext.propagate(() -> {
            assertTrue(propagatedContext.isBound());
            var propagatedContext2 = propagatedContext.plus(e3);
            assertFalse(propagatedContext2.isBound());
        });
    }

    @Test
    void testEmptyUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        PropagatedContext.getOrEmpty().plus(e1).propagate(() ->
            PropagatedContext.getOrEmpty().plus(e2).propagate(() ->
                PropagatedContext.getOrEmpty().plus(e3).propagate(() ->
                    PropagatedContext.empty().propagate(() -> {
                        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
                        assertIterableEquals(Collections.emptyList(), propagatedContext.getAllElements());
                        assertFalse(PropagatedContext.exists());
                    })
                )
            )
        );
    }

    @Test
    void testMinusUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        PropagatedContext.getOrEmpty().plus(e1).propagate(() ->
            PropagatedContext.getOrEmpty().plus(e2).propagate(() ->
                PropagatedContext.getOrEmpty().plus(e3).propagate(() -> {
                    PropagatedContext propagatedContext = PropagatedContext.get();
                    assertEquals(List.of(e1, e2, e3), propagatedContext.getAllElements());
                    assertEquals(List.of(e2, e3), propagatedContext.minus(e1).getAllElements());
                    assertEquals(List.of(e1, e3), propagatedContext.minus(e2).getAllElements());
                    assertEquals(List.of(e1, e2), propagatedContext.minus(e3).getAllElements());
                    assertEquals(List.of(e3), propagatedContext.minus(e1).minus(e2).getAllElements());
                    assertEquals(List.of(), propagatedContext.minus(e1).minus(e2).minus(e3).getAllElements());
                })
            )
        );
    }

    @Test
    void testMultipleElementsOfTheSameTypeUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        PropagatedContext.getOrEmpty().plus(e1).propagate(() ->
            PropagatedContext.getOrEmpty().plus(e2).propagate(() ->
                PropagatedContext.getOrEmpty().plus(e3).propagate(() -> {
                    PropagatedContext propagatedContext = PropagatedContext.get();
                    assertEquals(e3, propagatedContext.get(PropagatedElement.class));
                    assertEquals(e3, propagatedContext.find(PropagatedElement.class).get());
                    assertTrue(propagatedContext.getAllElements().containsAll(List.of(e1, e2, e3)));
                    assertEquals(List.of(e3, e2, e1), propagatedContext.findAll(PropagatedElement.class).toList());
                })
            )
        );
    }

    @Test
    void testRemovingElementUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
        PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            PropagatedContext.getOrEmpty().plus(e2).propagate(() -> {
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                PropagatedContext.getOrEmpty().plus(e3).propagate(() -> {
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    PropagatedContext.getOrEmpty().minus(e2).propagate(() ->
                        assertEquals(List.of(e3, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList())
                    );
                    PropagatedContext.getOrEmpty().minus(e2).minus(e1).propagate(() ->
                        assertEquals(List.of(e3), PropagatedContext.get().findAll(PropagatedElement.class).toList())
                    );
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                });
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            });
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
        });
        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
    }

    @Test
    void testReplacingElementUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();
        PropagatedElement e4 = new PropagatedElement();

        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
        PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            PropagatedContext.getOrEmpty().plus(e2).propagate(() -> {
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                PropagatedContext.getOrEmpty().plus(e3).propagate(() -> {
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                    PropagatedContext.getOrEmpty().replace(e2, e4).propagate(() ->
                        assertEquals(List.of(e3, e4, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList())
                    );
                    assertEquals(List.of(e3, e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
                });
                assertEquals(List.of(e2, e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
            });
            assertEquals(List.of(e1), PropagatedContext.get().findAll(PropagatedElement.class).toList());
        });
        assertEquals(List.of(), PropagatedContext.getOrEmpty().findAll(PropagatedElement.class).toList());
    }

    @Test
    void testRemovingMissingElementUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        assertThrows(NoSuchElementException.class, () ->
            PropagatedContext.getOrEmpty().minus(e1).propagate(() -> { /* no-op */ })
        );
    }

    @Test
    void testReplacingMissingElementUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        assertThrows(NoSuchElementException.class, () ->
            PropagatedContext.getOrEmpty().replace(e1, e2).propagate(() -> { /* no-op */ })
        );
    }

    @Test
    void testEmptyPropagatedContextCleansTheContextUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();

        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
        PropagatedContext.getOrEmpty().propagate(() -> {
            PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
                assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
            });
        });
        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
    }

    @Test
    void testEmptyPropagatedContextRestoresTheContextUsingRunnable() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e = new PropagatedElement();

        PropagatedContext.getOrEmpty().plus(e).propagate(() -> {
            assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
            PropagatedContext.getOrEmpty().propagate(() -> {
                PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
                    assertEquals(2, PropagatedContext.getOrEmpty().getAllElements().size());
                });
            });
            assertEquals(1, PropagatedContext.getOrEmpty().getAllElements().size());
        });
        assertEquals(0, PropagatedContext.getOrEmpty().getAllElements().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRestoringThreadContextUsingRunnable(boolean strict) {
        String before = "";
        CONTEXT_NAME_HOLDER.set(before);

        final String[] insideHolder = new String[1];

        PropagatedContext context = PropagatedContext.empty()
            .plus(new SetContextName("one", strict))
            .plus(new SetContextName("two", strict));

        context.propagate((Runnable) () -> insideHolder[0] = getCurrentContextName());
        String after = getCurrentContextName();

        assertEquals("two", insideHolder[0]);
        assertEquals(before, after);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRestoringThreadContextInAChainUsingRunnable(boolean strict) {
        String before = "";
        CONTEXT_NAME_HOLDER.set(before);

        final String[] firstInHolder = new String[1];
        final String[] secondInHolder = new String[1];
        final String[] secondOutHolder = new String[1];

        PropagatedContext.empty().plus(new SetContextName("first", strict)).propagate(() -> {
            firstInHolder[0] = getCurrentContextName();
            PropagatedContext.get().plus(new SetContextName("second", strict)).propagate(() -> {
                secondInHolder[0] = getCurrentContextName();
            });
            secondOutHolder[0] = getCurrentContextName();
        });
        String firstOut = getCurrentContextName();

        assertEquals("first", firstInHolder[0]);
        assertEquals("second", secondInHolder[0]);
        assertEquals("first", secondOutHolder[0]);
        assertEquals(before, firstOut);
    }

    static class PropagatedElement implements PropagatedContextElement {
    }

    private static final ThreadLocal<String> CONTEXT_NAME_HOLDER = ThreadLocal.withInitial(() -> "");

    private static String getCurrentContextName() {
        return CONTEXT_NAME_HOLDER.get();
    }

    private record SetContextName(String name,
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
