package io.micronaut.docs.config.env

import io.kotlintest.specs.AnnotationSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EnvironmentTest: AnnotationSpec(){

    @Test
    fun testRunEnvironment() {
        // tag::env[]
        val applicationContext = ApplicationContext.run("test", "android")
        val environment = applicationContext.environment

        assertTrue(environment.activeNames.contains("test"))
        assertTrue(environment.activeNames.contains("android"))
        // end::env[]
        applicationContext.close()
    }

    @Test
    fun testRunEnvironmentWithProperties() {
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
        val environment = applicationContext.environment

        assertEquals(
                "foo",
                environment.getProperty("micronaut.server.host", String::class.java).orElse("localhost")
        )
        // end::envProps[]
        applicationContext.close()
    }
}
