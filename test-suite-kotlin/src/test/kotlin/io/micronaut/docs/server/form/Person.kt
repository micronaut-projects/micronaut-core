package io.micronaut.docs.server.form

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

@ReflectiveAccess
@Introspected
data class Person(
    var firstName: String? = null,
    var lastName: String? = null,
    var age: Int = 0
)
