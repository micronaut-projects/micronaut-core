package io.micronaut.docs.config.properties

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import java.util.*

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
class EngineConfig {

    @NotBlank // <2>
    var manufacturer = "Ford" // <3>
    @Min(1L)
    var cylinders: Int = 0
    var crankShaft = CrankShaft()

    @ConfigurationProperties("crank-shaft")
    class CrankShaft { // <4>
        var rodLength: Optional<Double> = Optional.empty() // <5>
    }
}
// end::class[]