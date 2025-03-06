package io.micronaut.docs.config.property

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext

class EngineSpec : StringSpec({

    "test start vehicle with configuration" {
        val ctx = ApplicationContext.run(mapOf("my.engine.cylinders" to "8", "my.engine.manufacturer" to "Honda"))

        val engine = ctx.getBean(Engine::class.java)

        engine.manufacturer shouldBe "Honda"
        engine.cylinders() shouldBe 8

        ctx.close()
    }
})
