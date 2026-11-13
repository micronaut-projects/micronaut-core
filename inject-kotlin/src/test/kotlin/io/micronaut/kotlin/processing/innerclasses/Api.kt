package io.micronaut.kotlin.processing.innerclasses

import io.micronaut.core.annotation.Introspected

interface Api {
    @Introspected
    class Nested(val name: String)
}
