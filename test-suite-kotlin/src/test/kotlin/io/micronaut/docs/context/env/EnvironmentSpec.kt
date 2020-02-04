package io.micronaut.docs.context.env

import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EnvironmentSpec :  StringSpec({

    "test run environment"() {
        // tag::env[]
        val applicationContext = ApplicationContext.run("test", "android")
        val environment = applicationContext.getEnvironment()

        assertTrue(environment.getActiveNames().contains("test"))
        assertTrue(environment.getActiveNames().contains("android"))
        // end::env[]
        applicationContext.close()
    }

    "test run environment with properties"() {
        // tag::envProps[]
        val applicationContext = ApplicationContext.run(
                PropertySource.of(
                        "test",
                        mapOf(
                                "micronaut.server.host" to "foo",
                                "micronaut.server.port" to 8080
                        )
                ),
                "test", "android")
        val environment = applicationContext.getEnvironment()

        assertEquals(
                "foo",
                environment.getProperty("micronaut.server.host", String::class.java).orElse("localhost")
        )
        // end::envProps[]
        applicationContext.close()
    }
})
