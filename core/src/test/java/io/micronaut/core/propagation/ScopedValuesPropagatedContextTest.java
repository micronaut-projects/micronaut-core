package io.micronaut.core.propagation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class ScopedValuesPropagatedContextTest extends PropagatedContextLambdaTest {

    @BeforeAll
    static void setup() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.SCOPED_VALUE);
    }

    @AfterAll
    static void cleanup() {
        PropagatedContextConfiguration.reset();
    }

}
