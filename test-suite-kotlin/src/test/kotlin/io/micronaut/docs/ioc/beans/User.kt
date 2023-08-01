package io.micronaut.docs.ioc.beans

import io.micronaut.core.annotation.Introspected

// tag::class[]
@Introspected(accessKind = [Introspected.AccessKind.FIELD])
class User(
    val name: String // <1>
) {
    var age = 18 // <2>
}
// end::class[]
