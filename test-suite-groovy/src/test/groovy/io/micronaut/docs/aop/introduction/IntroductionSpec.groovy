package io.micronaut.docs.aop.introduction

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class IntroductionSpec extends Specification {

    void "test StubIntroduction"() {
        given:
        def applicationContext = ApplicationContext.run()

        // tag::test[]
        when:
        def stubExample = applicationContext.getBean(StubExample)

        then:
        stubExample.number == 10
        stubExample.date == null
        // end::test[]

        cleanup:
        applicationContext.stop()
    }
}
