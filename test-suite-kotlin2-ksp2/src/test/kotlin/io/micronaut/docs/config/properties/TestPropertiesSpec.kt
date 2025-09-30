package io.micronaut.docs.config.properties

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext

class TestPropertiesSpec: StringSpec({

    "test test properties with defaults" {
        val applicationContext = ApplicationContext.run()

        applicationContext.getBean(TestProperties1::class.java).enabled.shouldBe(true)
        applicationContext.getBean(TestProperties2::class.java).enabled.shouldBe(true)

        applicationContext.close()
    }

})
