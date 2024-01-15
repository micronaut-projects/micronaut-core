package io.micronaut.docs.config.properties

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext

class TestPropertiesSpec: StringSpec({

    "test test properties with defaults" {
        val applicationContext = ApplicationContext.run()

        val props = applicationContext.getBean(TestProperties::class.java)

        props.enabled.shouldBe(true)

        applicationContext.close()
    }

})
