package io.micronaut.docs.http.server.bind.type

// tag::class[]
import io.micronaut.core.annotation.Introspected

@Introspected
class ShoppingCart {
    String sessionId
    Integer total
}
// end::class[]
