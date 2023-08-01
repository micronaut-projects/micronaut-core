package io.micronaut.core.beans

import io.micronaut.core.annotation.Introspected

@Introspected
data class TestEntity3(val firstName: String = "Denis",
                       val lastName: String,
                       val job: String? = "IT",
                       val age: Int) {

}
