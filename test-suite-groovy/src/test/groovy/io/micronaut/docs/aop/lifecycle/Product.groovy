package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import io.micronaut.context.annotation.Parameter
import jakarta.annotation.PreDestroy
// end::imports[]

// tag::class[]
@ProductBean // <1>
class Product {
    final String productName
    boolean active = false

    Product(@Parameter String productName) { // <2>
        this.productName = productName
    }

    @PreDestroy // <3>
    void disable() {
        active = false
    }
}
// end::class[]