package io.micronaut.docs.config.env

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource

class EnvironmentTest : AnnotationSpec() {


    @Test
    fun testRunEnvironment() {
        // tag::env[]
        val applicationContext = ApplicationContext.run("test", "android")
        val environment = applicationContext.environment

        environment.activeNames.shouldContain("test")
        environment.activeNames.shouldContain("android")
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

        environment.getProperty("micronaut.server.host", String::class.java).orElse("localhost") shouldBe "foo"
        // end::envProps[]
        applicationContext.close()
    }
}
