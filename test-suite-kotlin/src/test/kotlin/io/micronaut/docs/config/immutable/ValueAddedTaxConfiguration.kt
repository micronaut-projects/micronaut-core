package io.micronaut.docs.config.immutable

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
// end::imports[]

@Requires(property = "spec.name", value = "ValueAddedTaxConfigurationTest")
// tag::class[]
@ConfigurationProperties("vat")
data class ValueAddedTaxConfiguration(
    @field:NotNull val percentage: BigDecimal) // <1>
// end::class[]
