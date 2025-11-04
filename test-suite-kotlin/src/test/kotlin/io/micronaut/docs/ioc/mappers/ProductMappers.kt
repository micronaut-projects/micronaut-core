package io.micronaut.docs.ioc.mappers


// tag::class[]
import io.micronaut.context.annotation.Mapper.Mapping
import jakarta.inject.Singleton

@Singleton
abstract class ProductMappers {
    @Mapping(to = "price", from = "#{product.price * 2}", format = "$#.00")
    @Mapping(to = "distributor", from = "#{this.getDistributor()}")
    abstract fun toProductDTO(product: Product): ProductDTO
    fun getDistributor() : String = "Great Product Company"
}

// tag::class[]
