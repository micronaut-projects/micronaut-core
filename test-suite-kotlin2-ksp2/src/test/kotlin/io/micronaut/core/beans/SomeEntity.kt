package io.micronaut.core.beans

import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

@Introspected
data class SomeEntity @Creator constructor(
        val id: Long? = null,
        val something: String? = null
)
