package io.micronaut.kotlin.processing.aop.introduction.delegation

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DelegatingIntroductionAdviceSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test that delegation advice works"() {
        given:
        DelegatingIntroduced delegating = (DelegatingIntroduced)context.getBean(Delegating)

        expect:
        delegating.test2() == 'good'
        delegating.test() == 'good'
    }
}
