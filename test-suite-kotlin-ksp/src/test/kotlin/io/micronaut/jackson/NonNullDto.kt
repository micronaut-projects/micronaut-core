package io.micronaut.jackson

import io.micronaut.core.annotation.Introspected

@Introspected
data class NonNullDto(
    val longField: Long,
)
