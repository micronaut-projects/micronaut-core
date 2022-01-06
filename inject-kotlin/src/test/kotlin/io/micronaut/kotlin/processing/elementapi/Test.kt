package io.micronaut.kotlin.processing.elementapi

class Test private constructor(val name: String) {

    companion object {
        fun forName(): Test {
            return Test("default")
        }
    }
}
