package io.micronaut.docs.config.immutable

import io.micronaut.context.annotation.Requires

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
// end::imports[]

@Requires(property = "spec.name", value = "ValueAddedTaxConfigurationSpec")
// tag::class[]
@ConfigurationProperties("vat")
data class ValueAddedTaxConfiguration(
    @NotNull val percentage: BigDecimal) // <1>
// end::class[]
