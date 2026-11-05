
package io.micronaut.jackson

import io.micronaut.core.annotation.Introspected

@Introspected
data class DefaultConstructorDto(
    val longField: Long = 22,
)

@Introspected
data class RequireConstructorParamDto(
    val longField: Long
)
