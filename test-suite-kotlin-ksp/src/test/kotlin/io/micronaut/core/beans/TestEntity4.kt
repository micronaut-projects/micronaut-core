package io.micronaut.core.beans

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

@Introspected
data class TestEntity4(val firstName: String = "Denis",
                       val lastName: String = "Stepanov",
                       val job: String? = "IT",
                       val age: Int = 99) {

    @Executable
    fun test4(i: Int? = 88) : String {
        return "$i"
    }

    @Executable
    fun test3(i: Int = 88) : String {
        return "$i"
    }

    @Executable
    fun test2(a: String = "A") : String {
        return a
    }

    @Executable
    fun test1(a: String = "A", b: String, i: Int = 99) : String {
        return "$a $b $i"
    }

}
