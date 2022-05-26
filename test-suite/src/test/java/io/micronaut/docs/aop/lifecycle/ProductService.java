package io.micronaut.docs.aop.lifecycle;

// tag::imports[]
import jakarta.inject.Singleton;
import java.util.*;
// end::imports[]

// tag::class[]
@Singleton
public class ProductService {
    private final Map<String, Product> products = new HashMap<>();

    void addProduct(Product product) {
        products.put(product.getProductName(), product);
    }

    void removeProduct(Product product) {
        product.setActive(false);
        products.remove(product.getProductName());
    }

    Optional<Product> findProduct(String name) {
        return Optional.ofNullable(products.get(name));
    }
}
// end::class[]
