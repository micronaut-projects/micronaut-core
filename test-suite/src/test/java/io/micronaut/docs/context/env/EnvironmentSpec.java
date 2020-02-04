package io.micronaut.docs.context.env;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.util.CollectionUtils;
import org.junit.Test;

public class EnvironmentSpec {

    @Test
    public void testRunEnvironment() {
        // tag::env[]
        ApplicationContext applicationContext = ApplicationContext.run("test", "android");
        Environment environment = applicationContext.getEnvironment();

        assertTrue(environment.getActiveNames().contains("test"));
        assertTrue(environment.getActiveNames().contains("android"));
        // end::env[]

        applicationContext.close();

    }

    @Test
    public void testRunEnvironmentWithProperties() {
        // tag::envProps[]
        ApplicationContext applicationContext = ApplicationContext.run(
                PropertySource.of(
                        "test",
                        CollectionUtils.mapOf(
                            "micronaut.server.host", "foo",
                            "micronaut.server.port", 8080
                        )
                ),
                "test", "android");
        Environment environment = applicationContext.getEnvironment();

        assertEquals(
                "foo",
                environment.getProperty("micronaut.server.host", String.class).orElse("localhost")
        );
        // end::envProps[]

        applicationContext.close();
    }
}
