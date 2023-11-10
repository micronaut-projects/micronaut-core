package io.micronaut.docs.context.env

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource

class EnvironmentSpec :  StringSpec({

    "test run environment" {
        // tag::env[]
        val applicationContext = ApplicationContext.run("test", "android")
        val environment = applicationContext.environment

        environment.activeNames.shouldContain("test")
        environment.activeNames.shouldContain("android")
        // end::env[]
        applicationContext.close()
    }

    "test run environment with properties" {
        // tag::envProps[]
        val applicationContext = ApplicationContext.run(
            PropertySource.of(
                "test",
                mapOf(
                    "micronaut.server.host" to "foo",
                    "micronaut.server.port" to 8080
                )
            ),
            "test", "android"
        )
        val environment = applicationContext.environment

        environment.getProperty("micronaut.server.host", String::class.java).orElse("localhost") shouldBe "foo"
        // end::envProps[]
        applicationContext.close()
    }
})
