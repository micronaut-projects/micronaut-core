package io.micronaut.docs.server.form

import io.micronaut.core.annotation.Introspected

@Introspected
data class Person(val firstName: String,
                  val lastName: String,
                  val age: Int)
