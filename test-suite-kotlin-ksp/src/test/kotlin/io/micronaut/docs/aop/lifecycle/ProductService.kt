package io.micronaut.docs.aop.lifecycle

// tag::imports[]
import java.util.*
import jakarta.inject.Singleton
// end::imports[]

// tag::class[]
@Singleton
class ProductService {
    private val products: MutableMap<String, Product> = HashMap()
    fun addProduct(product: Product) {
        products[product.productName] = product
    }

    fun removeProduct(product: Product) {
        product.active = false
        products.remove(product.productName)
    }

    fun findProduct(name: String): Optional<Product> {
        return Optional.ofNullable(products[name])
    }
}
// end::class[]
