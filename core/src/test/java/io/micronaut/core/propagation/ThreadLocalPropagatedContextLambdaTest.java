package io.micronaut.core.propagation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class ThreadLocalPropagatedContextLambdaTest extends PropagatedContextLambdaTest {

    @BeforeAll
    static void setup() {
        PropagatedContextConfiguration.set(PropagatedContextConfiguration.Mode.THREAD_LOCAL);
    }

    @AfterAll
    static void cleanup() {
        PropagatedContextConfiguration.reset();
    }

}
