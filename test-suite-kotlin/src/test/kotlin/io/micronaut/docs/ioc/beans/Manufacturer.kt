package io.micronaut.docs.ioc.beans

import io.micronaut.core.annotation.Introspected

@Introspected
data class Manufacturer(
        var id: Long?,
        var name: String
)