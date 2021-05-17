package io.micronaut.inject.ordered

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PickHighestPriorityBeanSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test pick highest priority bean when ordered present"() {
        expect:
        context.getBean(Product) instanceof HighValueProduct
        context.getBean(Product) instanceof HighValueProduct

        where:
        repeat << (1..10) // repeat ten times to ensure always the same result
    }
}
