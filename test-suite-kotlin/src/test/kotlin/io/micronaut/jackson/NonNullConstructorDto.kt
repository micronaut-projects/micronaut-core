package io.micronaut.jackson

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
data class NonNullConstructorDto(
    @JsonProperty("longField") val longField: Long,
)
