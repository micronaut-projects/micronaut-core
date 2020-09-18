package io.micronaut.docs.config.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class OrderTest {

    @Test
    public void testOrderOnFactories() {
        ApplicationContext applicationContext = ApplicationContext.run();
        List<RateLimit> rateLimits = applicationContext.streamOfType(RateLimit.class)
                .collect(Collectors.toList());

        assertEquals(
                2,
                rateLimits.size()
        );
        assertEquals(1000L, rateLimits.get(0).getLimit().longValue());
        assertEquals(100L, rateLimits.get(1).getLimit().longValue());

        applicationContext.close();
    }
}
