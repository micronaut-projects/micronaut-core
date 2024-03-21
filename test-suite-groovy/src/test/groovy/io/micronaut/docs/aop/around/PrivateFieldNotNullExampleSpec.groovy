package io.micronaut.docs.aop.around

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class PrivateFieldNotNullExampleSpec extends Specification {

    void "test not null"() {
        when:
        def applicationContext = ApplicationContext.run()
        def exampleBean = applicationContext.getBean(PrivateFieldNotNullExample)

        def work = exampleBean.doWork('work')

        then:
        work == 'work'

        cleanup:
        applicationContext.close()
    }
}
