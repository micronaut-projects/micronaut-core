
package io.micronaut.jackson

import io.micronaut.core.annotation.Introspected

@Introspected
data class NullConstructorDto(
    val longField: Long? = null
)
