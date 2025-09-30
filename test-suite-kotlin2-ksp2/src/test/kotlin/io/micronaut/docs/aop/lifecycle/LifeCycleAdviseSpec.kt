package io.micronaut.docs.aop.lifecycle

import io.micronaut.context.ApplicationContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifeCycleAdviseSpec {
    @Test
    fun testLifeCycleAdvise() {
        ApplicationContext.run().use { applicationContext ->
            val productService =
                applicationContext.getBean(ProductService::class.java)
            val product =
                applicationContext.createBean(Product::class.java, "Apple") //
            assertTrue(product.active)
            assertTrue(productService.findProduct("APPLE").isPresent)

            applicationContext.destroyBean(product)
            assertFalse(product.active)
            assertFalse(productService.findProduct("APPLE").isPresent)
        }
    }
}