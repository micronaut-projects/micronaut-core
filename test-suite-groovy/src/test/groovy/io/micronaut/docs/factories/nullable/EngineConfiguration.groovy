package io.micronaut.docs.factories.nullable

import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.util.Toggleable

import javax.validation.constraints.NotNull

// tag::class[]
@EachProperty("engines")
class EngineConfiguration implements Toggleable {
    boolean enabled = true
    @NotNull
    Integer cylinders
}
// end::class[]
