package io.micronaut.core.propagation;

import io.micronaut.core.propagation.PropagatedContextConfiguration.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropagatedContextConfigurationTest {

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
    }

    @Test
    void defaultsToScopedValue() {
        PropagatedContextConfiguration.reset();
        Mode mode = PropagatedContextConfiguration.get();
        assertEquals(Mode.SCOPED_VALUE, mode);
    }

    @Test
    void allowsSelectingModes() {
        PropagatedContextConfiguration.set(Mode.THREAD_LOCAL);
        Mode threadLocal = PropagatedContextConfiguration.get();
        assertEquals(Mode.THREAD_LOCAL, threadLocal);

        PropagatedContextConfiguration.set(Mode.SCOPED_VALUE);
        Mode scopedValue = PropagatedContextConfiguration.get();
        assertEquals(Mode.SCOPED_VALUE, scopedValue);
    }

    @Test
    void togglesMirrorAvailableModes() {
        PropagatedContextConfiguration.reset();

        PropagatedContextConfiguration.set(Mode.THREAD_LOCAL);
        Mode threadOnly = PropagatedContextConfiguration.get();
        assertEquals(Mode.THREAD_LOCAL, threadOnly);

        PropagatedContextConfiguration.set(Mode.SCOPED_VALUE);
        Mode scopedAgain = PropagatedContextConfiguration.get();
        assertEquals(Mode.SCOPED_VALUE, scopedAgain);
    }
}
