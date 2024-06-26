
package io.micronaut.jackson

import io.micronaut.core.annotation.Introspected

@Introspected
data class NullDto(
    val longField: Long? = null
)
