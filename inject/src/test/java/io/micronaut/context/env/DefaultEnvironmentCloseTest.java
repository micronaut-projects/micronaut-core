package io.micronaut.context.env;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContextBuilder;
import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultEnvironmentCloseTest {

    @Test
    void closesEveryLocatorAndStopsWhenSomeFailToClose() {
        ClosingLocator first = new ClosingLocator("first fails");
        ClosingLocator second = new ClosingLocator("second fails");
        ClosingLocator third = new ClosingLocator(null);
        DefaultApplicationContextBuilder builder = (DefaultApplicationContextBuilder) ApplicationContext.builder()
            .enableDefaultPropertySources(false)
            .propertySourcesLocator(first)
            .propertySourcesLocator(second)
            .propertySourcesLocator(third);
        DefaultEnvironment environment = new DefaultEnvironment(builder);
        environment.start();

        RuntimeException e = assertThrows(RuntimeException.class, environment::close);

        assertTrue(e.getMessage().contains("first fails"));
        assertEquals(1, e.getSuppressed().length);
        assertTrue(e.getSuppressed()[0].getMessage().contains("second fails"));
        assertEquals(1, first.closed);
        assertEquals(1, second.closed);
        assertEquals(1, third.closed);
        assertFalse(environment.isRunning());
    }

    private static final class ClosingLocator implements PropertySourcesLocator, Closeable {
        private final String failure;
        private int closed;

        private ClosingLocator(String failure) {
            this.failure = failure;
        }

        @Override
        public Collection<PropertySource> load(Environment environment) {
            return List.of();
        }

        @Override
        public void close() {
            closed++;
            if (failure != null) {
                throw new IllegalStateException(failure);
            }
        }
    }
}
