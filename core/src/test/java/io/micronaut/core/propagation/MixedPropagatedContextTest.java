package io.micronaut.core.propagation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class MixedPropagatedContextTest {

    @Test
    void testMixed() {
        PropagatedElement e1 = new PropagatedElement();
        PropagatedElement e2 = new PropagatedElement();
        PropagatedElement e3 = new PropagatedElement();

        PropagatedContext.getOrEmpty().plus(e1).propagate(() -> {
            PropagatedContext propagatedContext1 = PropagatedContext.get();
            assertIterableEquals(List.of(e1), propagatedContext1.getAllElements());
            // Propagated using the Scoped Values
            PropagatedContextConfiguration.Mode previous = PropagatedContextConfiguration.get();
            PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
            try (PropagatedContext.Scope ignore = propagatedContext1.plus(e2).propagate()) {
                // ThreadLocal propagation
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
    }

    static class PropagatedElement implements PropagatedContextElement {
    }

}
