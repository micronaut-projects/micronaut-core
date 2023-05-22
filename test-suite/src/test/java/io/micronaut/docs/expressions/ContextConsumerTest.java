package io.micronaut.docs.expressions;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
public class ContextConsumerTest {
    @Test
    void testContextConsumer(ContextConsumer consumer) {
        Assertions.assertTrue(consumer.randomField > 0 && consumer.randomField < 20);
    }
}
