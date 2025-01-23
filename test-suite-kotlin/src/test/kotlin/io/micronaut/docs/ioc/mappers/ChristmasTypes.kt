package io.micronaut.docs.ioc.mappers

import io.micronaut.core.annotation.Introspected

// tag::beans[]
@Introspected
data class ChristmasPresent (
    val packagingColor: String?,
    val type: String?,
    val weight: Float?,
    val greetingCard: String?
)

@Introspected
data class PresentPackaging (
    val weight: Float?,
    val color: String?
)

@Introspected
data class Present (
    val weight: Float?,
    val type: String?
)
// end::beans[]

