package io.micronaut.docs.ioc.mappers;

// tag::class[]
import io.micronaut.context.annotation.Mapper;
import io.micronaut.context.annotation.Mapper.Mapping;
import jakarta.inject.Singleton;

@Singleton
@Mapper(conflictStrategy = Mapper.ConflictStrategy.ERROR)
public interface ProductMappers2 {
    @Mapping(
        to = "price",
        from = "#{product.price * 2}")
    @Mapping(
        to = "distributor",
        from = "#{this.getDistributor()}"
    )
    ProductDTO toProductDTO(Product product);

    default String getDistributor() {
        return "Great Product Company";
    }
}
// tag::end[]
