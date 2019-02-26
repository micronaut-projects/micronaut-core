package io.micronaut.inject.visitor.beans

import io.micronaut.core.annotation.Introspected

@Introspected
data class TestBean(
        val name : String,
        val age : Int,
        val stringArray : Array<String>) {
    var stuff : String = "default"
    var flag : Boolean = false
}