package io.micronaut.docs.aop.around

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class AroundSpec extends Specification {

    // tag::test[]
    void "test not null"() {
        when:
        def applicationContext = ApplicationContext.run()
        def exampleBean = applicationContext.getBean(NotNullExample)

        exampleBean.doWork(null)

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Null parameter [taskName] not allowed'

        cleanup:
        applicationContext.close()
    }
    // end::test[]
}
