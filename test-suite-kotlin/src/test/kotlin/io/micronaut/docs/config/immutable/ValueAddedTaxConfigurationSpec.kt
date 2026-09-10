package io.micronaut.docs.config.immutable

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ValueAddedTaxConfigurationSpec {

    @Test
    fun immutableConfigurationViaRecords() {
        ApplicationContext.run(
            mapOf(
                "spec.name" to "ValueAddedTaxConfigurationTest",
                "vat.percentage" to "21.0"
            )
        ).use { context ->
            assertTrue(context.containsBean(ValueAddedTaxConfiguration::class.java))
            assertEquals(BigDecimal("21.0"), context.getBean(ValueAddedTaxConfiguration::class.java).percentage)
        }
    }
}
