package io.micronaut.docs.config.immutable;

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
// end::imports[]

@Requires(property="spec.name", value="ValueAddedTaxConfigurationTest")
// tag::class[]
@ConfigurationProperties("vat")
public record ValueAddedTaxConfiguration(
    @NonNull @NotNull BigDecimal percentage) { // <1>
}
// end::class[]
