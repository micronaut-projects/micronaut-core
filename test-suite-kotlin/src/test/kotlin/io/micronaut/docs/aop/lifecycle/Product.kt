package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.context.annotation.Parameter
import jakarta.annotation.PreDestroy
// end::imports[]

// tag::class[]
@ProductBean // <1>
class Product(@param:Parameter val productName: String ) { // <2>

    var active: Boolean = false
    @PreDestroy
    fun disable() { // <3>
        active = false
    }
}
// end::class[]