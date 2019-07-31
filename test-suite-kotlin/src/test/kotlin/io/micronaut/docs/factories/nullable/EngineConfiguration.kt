package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.util.Toggleable
import javax.validation.constraints.NotNull

// tag::class[]
@EachProperty("engines")
class EngineConfiguration : Toggleable {

    val enabled = true

    @NotNull
    val cylinders: Int? = null

    override fun isEnabled(): Boolean {
        return enabled
    }
}
// end::class[]
