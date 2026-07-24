package io.micronaut.core.beans

import io.micronaut.core.annotation.Introspected

interface Issue12262A {
    val foo: String
}

interface Issue12262B {
    val foo: String
}

@Introspected
data class Issue12262Bean(
    override val foo: String
) : Issue12262A, Issue12262B
