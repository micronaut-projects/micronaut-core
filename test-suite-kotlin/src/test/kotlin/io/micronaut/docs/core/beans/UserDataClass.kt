package io.micronaut.docs.core.beans

import io.micronaut.core.annotation.Introspected

/**
 * https://kotlinlang.org/docs/data-classes.html
 */
//tag::dataclass[]
@Introspected
data class UserDataClass(val name: String)
//end::dataclass[]
