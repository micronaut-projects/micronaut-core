package io.micronaut.docs.config.immutable

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.context.ApplicationContext
import java.math.BigDecimal

class ValueAddedTaxConfigurationSpec : StringSpec({

    "immutable configuration via a Kotlin data class needs no @ConfigurationInject" {
        val applicationContext = ApplicationContext.run(
            mapOf(
                "spec.name" to "ValueAddedTaxConfigurationSpec",
                "vat.percentage" to "21.0"
            )
        )

        applicationContext.containsBean(ValueAddedTaxConfiguration::class.java).shouldBe(true)
        applicationContext.getBean(ValueAddedTaxConfiguration::class.java)
            .percentage.shouldBe(BigDecimal("21.0"))

        applicationContext.close()
    }
})
