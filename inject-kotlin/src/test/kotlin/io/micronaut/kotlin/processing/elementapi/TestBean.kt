package io.micronaut.kotlin.processing.elementapi

import io.micronaut.core.annotation.Introspected

@Introspected
class TestBean {
    var flag = false
    var name: String? = null
    var age = 0
    var stringArray: Array<String>? = null
}
