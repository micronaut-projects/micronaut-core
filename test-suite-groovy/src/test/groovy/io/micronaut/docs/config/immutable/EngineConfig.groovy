package io.micronaut.docs.config.immutable

import io.micronaut.context.annotation.ConfigurationInject


// tag::imports[]

import io.micronaut.context.annotation.*
import io.micronaut.core.bind.annotation.Bindable
import javax.annotation.Nullable
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
class EngineConfig {

    private final String manufacturer
    private final int cylinders
    private final CrankShaft crankShaft

    @ConfigurationInject // <2>
    EngineConfig(
                  @Bindable(defaultValue = "Ford") @NotBlank String manufacturer, // <3>
                  @Min(1L) int cylinders, // <4>
                  @NotNull CrankShaft crankShaft) {
        this.manufacturer = manufacturer
        this.cylinders = cylinders
        this.crankShaft = crankShaft
    }

    String getManufacturer() {
        return manufacturer
    }

    int getCylinders() {
        return cylinders
    }

    CrankShaft getCrankShaft() {
        return crankShaft
    }

    @ConfigurationProperties("crank-shaft")
    static class CrankShaft { // <5>
        private final Double rodLength // <6>

        @ConfigurationInject
        CrankShaft(@Nullable Double rodLength) {
            this.rodLength = rodLength
        }

        Optional<Double> getRodLength() {
            return Optional.ofNullable(rodLength)
        }
    }
}
// end::class[]
