package io.micronaut.docs.config.immutable


// tag::imports[]
import io.micronaut.context.annotation.*
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.*
import java.util.Optional

// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
data class EngineConfig @ConfigurationInject // <2>
    constructor(
        @Bindable(defaultValue = "Ford") @NotBlank // <3>
        val manufacturer: String,
        @Min(1L) // <4>
        val cylinders: Int,
        @NotNull val crankShaft: CrankShaft) {

    @ConfigurationProperties("crank-shaft")
    data class CrankShaft @ConfigurationInject
    constructor(// <5>
            private val rodLength: Double? // <6>
    ) {

        fun getRodLength(): Optional<Double> {
            return Optional.ofNullable(rodLength)
        }
    }
}
// end::class[]
