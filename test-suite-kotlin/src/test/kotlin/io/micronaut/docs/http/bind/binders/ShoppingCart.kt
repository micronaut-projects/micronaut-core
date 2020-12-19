package io.micronaut.docs.http.bind.binders

// tag::class[]
import io.micronaut.core.annotation.Introspected

@Introspected
class ShoppingCart {
    var sessionId: String? = null
    var total: Int? = null
}
// end::class[]
