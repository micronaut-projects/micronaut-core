package io.micronaut.docs.config.property

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import org.junit.Test

import java.util.LinkedHashMap

import org.junit.Assert.assertEquals

class EngineSpec : StringSpec({

    "test start vehicle with configuration" {
        val ctx = ApplicationContext.run(mapOf("my.engine.cylinders" to "8", "my.engine.manufacturer" to "Honda"))

        val engine = ctx.getBean(Engine::class.java)

        engine.manufacturer shouldBe "Honda"
        engine.cylinders() shouldBe 8

        ctx.close()
    }
})
