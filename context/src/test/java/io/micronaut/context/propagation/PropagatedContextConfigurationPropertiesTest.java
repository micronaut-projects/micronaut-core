package io.micronaut.context.propagation;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.propagation.PropagatedContextConfiguration;
import io.micronaut.core.propagation.PropagatedContextConfiguration.Mode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PropagatedContextConfigurationPropertiesTest {

    @AfterEach
    void cleanup() {
        PropagatedContextConfiguration.reset();
    }

    @Test
    void usesDefaultWhenNoPropertySet() {
        try (ApplicationContext context = ApplicationContext.run()) {
            PropagatedContextConfigurationProperties properties = context.getBean(PropagatedContextConfigurationProperties.class);
            assertEquals(Mode.THREAD_LOCAL, properties.getPropagation());
            assertEquals(Mode.THREAD_LOCAL, PropagatedContextConfiguration.get());
        }
    }

    @Test
    void appliesScopedValueConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.propagation", "scoped-value"))) {
            PropagatedContextConfigurationProperties properties = context.getBean(PropagatedContextConfigurationProperties.class);
            assertEquals(Mode.SCOPED_VALUE, properties.getPropagation());
            assertEquals(Mode.SCOPED_VALUE, PropagatedContextConfiguration.get());
        }
    }

    @Test
    void appliesThreadLocalConfiguration() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("micronaut.propagation", "thread-local"))) {
            PropagatedContextConfigurationProperties properties = context.getBean(PropagatedContextConfigurationProperties.class);
            assertEquals(Mode.THREAD_LOCAL, properties.getPropagation());
            assertEquals(Mode.THREAD_LOCAL, PropagatedContextConfiguration.get());
        }
    }
}
