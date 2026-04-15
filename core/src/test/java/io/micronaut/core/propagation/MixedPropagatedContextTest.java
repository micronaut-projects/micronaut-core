package io.micronaut.core.propagation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class MixedPropagatedContextTest {

    @Test
    void testMixed() {
        PropagatedContextConfiguration.Mode original = PropagatedContextConfiguration.get();
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
        try {
            PropagatedElement e1 = new PropagatedElement();
            PropagatedElement e2 = new PropagatedElement();
            PropagatedElement e3 = new PropagatedElement();

            PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
                PropagatedContext propagatedContext1 = PropagatedContext.get();
                assertIterableEquals(List.of(e1), propagatedContext1.getAllElements());
                // Propagated using Scoped Values
                PropagatedContextConfiguration.Mode previous = PropagatedContextConfiguration.get();
                PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
                try (PropagatedContext.Scope ignore = propagatedContext1.plus(e2).propagate()) {
                    // Thread-local propagation
                    PropagatedContext propagatedContext2 = PropagatedContext.get();
                    assertIterableEquals(List.of(e1, e2), propagatedContext2.getAllElements());
                    PropagatedContextConfiguration.set(previous);
                    propagatedContext2.plus(e3).propagate(() -> {
                        // Again Scoped Values propagation
                        assertIterableEquals(List.of(e1, e2, e3), PropagatedContext.get().getAllElements());
                    });
                    PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
                } finally {
                    PropagatedContextConfiguration.set(previous);
                }
            });
        } finally {
            PropagatedContextConfiguration.set(original);
        }
    }

    static class PropagatedElement implements PropagatedContextElement {
    }

}
