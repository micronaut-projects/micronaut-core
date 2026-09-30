package io.micronaut.docs.server.asyncbody

import io.micronaut.core.annotation.Introspected

@Introspected
data class Person(var name: String = "", var age: Int = 0)
