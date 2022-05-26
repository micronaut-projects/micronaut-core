package io.micronaut.inject.ordered

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
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

    void 'test that duplicate order results in non unique bean exception'() {
        when:
        context.getBean(Fruit)

        then:
        thrown(NonUniqueBeanException)
    }
}
