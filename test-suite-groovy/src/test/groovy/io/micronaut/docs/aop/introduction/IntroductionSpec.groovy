package io.micronaut.docs.aop.introduction

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class IntroductionSpec extends Specification {

    void "test StubIntroduction"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        // tag::test[]
        when:
        StubExample stubExample = applicationContext.getBean(StubExample.class)

        then:
        stubExample.getNumber() == 10
        stubExample.getDate() == null
        // end::test[]

        cleanup:
        applicationContext.stop()
    }
}
