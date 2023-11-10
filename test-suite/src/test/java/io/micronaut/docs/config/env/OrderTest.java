package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderTest {

    @Test
    void testOrderOnFactories() {
        ApplicationContext applicationContext = ApplicationContext.run();
        List<RateLimit> rateLimits = applicationContext.streamOfType(RateLimit.class)
                .toList();

        assertEquals(
                2,
                rateLimits.size()
        );
        assertEquals(1000L, rateLimits.get(0).getLimit().longValue());
        assertEquals(100L, rateLimits.get(1).getLimit().longValue());

        applicationContext.close();
    }
}
