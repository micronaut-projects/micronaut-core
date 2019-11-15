package io.micronaut.docs.config.itfce

// tag::imports[]
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.*
import java.util.Optional

// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine") // <1>
interface EngineConfig {

    @get:Bindable(defaultValue = "Ford") // <2>
    @get:NotBlank // <3>
    val manufacturer: String

    @get:Min(1L)
    val cylinders: Int

    @get:NotNull
    val crankShaft: CrankShaft // <4>

    @ConfigurationProperties("crank-shaft")
    interface CrankShaft { // <5>
        val rodLength: Optional<Double> // <6>
    }
}
// end::class[]

