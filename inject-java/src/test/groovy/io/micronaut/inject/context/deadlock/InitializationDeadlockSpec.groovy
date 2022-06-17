package io.micronaut.inject.context.deadlock

import io.micronaut.context.ApplicationContext
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

class InitializationDeadlockSpec extends Specification {

    @Timeout(10)
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/3910')
    def 'nested initialization on different threads should not lead to deadlock'() {
        given:
            def ctx = ApplicationContext.run()

        expect:
            ctx.getBean(BeanA) != null
            ctx.getBean(BeanB).visitedFromA
    }

}