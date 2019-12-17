package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

import javax.annotation.concurrent.Immutable

@Introspected
@Immutable
class Vehicle @Creator constructor(val make: String, val model: String, val axels: Int) { // <1>

    constructor(make: String, model: String) : this(make, model, 2) {}
}
// end::class[]
