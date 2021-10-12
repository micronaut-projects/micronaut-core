package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EnvironmentTest {

    @Test
    void testRunEnvironment() {
        // tag::env[]
        ApplicationContext applicationContext = ApplicationContext.run("test", "android");
        Environment environment = applicationContext.getEnvironment();

        Assertions.assertTrue(environment.getActiveNames().contains("test"));
        Assertions.assertTrue(environment.getActiveNames().contains("android"));
        // end::env[]
        applicationContext.close()

    }

    @Test
    void testRunEnvironmentWithProperties() {
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

        Assertions.assertEquals(
                "foo",
                environment.getProperty("micronaut.server.host", String.class).orElse("localhost")
        );
        // end::envProps[]
        applicationContext.close()

    }
}
