package io.micronaut.docs.aop.lifecycle

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification


class LifeCycleAdviseSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run()

    void testLifeCycleAdvise() {
        when:
        final ProductService productService = applicationContext.getBean(ProductService.class);

        Product product = applicationContext.createBean(Product.class, "Apple"); // <1>

        then:
        product.active
        productService.findProduct("APPLE").isPresent()

        when:
        applicationContext.destroyBean(product); // <2>
        then:
        !product.active
        !productService.findProduct("APPLE").isPresent()
    }
}
