package io.micronaut.docs.aop.lifecycle

import io.micronaut.context.ApplicationContext
import io.micronaut.core.version.SemanticVersion
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.environment.Jvm


// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
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
