package io.micronaut.docs.aop.lifecycle;

// tag::imports[]
import io.micronaut.context.annotation.Parameter;

import jakarta.annotation.PreDestroy;
// end::imports[]

// tag::class[]
@ProductBean // <1>
public class Product {
    private final String productName;
    private boolean active = false;

    public Product(@Parameter String productName) { // <2>
        this.productName = productName;
    }

    public String getProductName() {
        return productName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @PreDestroy // <3>
    void disable() {
        active = false;
    }
}
// end::class[]