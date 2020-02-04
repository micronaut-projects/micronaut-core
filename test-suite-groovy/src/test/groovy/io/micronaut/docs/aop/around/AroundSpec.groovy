package io.micronaut.docs.aop.around

import io.micronaut.context.ApplicationContext
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import spock.lang.Specification

class AroundSpec extends Specification {

    // tag::test[]
    void "test not null"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run()
        NotNullExample exampleBean = applicationContext.getBean(NotNullExample.class)

        exampleBean.doWork(null)

        then:
        IllegalArgumentException ex = thrown()
        ex.message == 'Null parameter [taskName] not allowed'

        cleanup:
        applicationContext.close()
    }
    // end::test[]
}
