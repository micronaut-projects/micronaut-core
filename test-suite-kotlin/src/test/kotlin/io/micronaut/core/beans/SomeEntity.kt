package io.micronaut.core.beans

import io.micronaut.core.annotation.Introspected

@Introspected
data class SomeEntity(
        val id: Long? = null,
        val something: String? = null
)