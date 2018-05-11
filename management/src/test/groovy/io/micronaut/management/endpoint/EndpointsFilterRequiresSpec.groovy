package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.Specification

class EndpointsFilterRequiresSpec extends Specification {

    def "EndpointsFilter is loaded if micronaut.security.enabled=false"() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.security.enabled': false])

        when:
        context.getBean(EndpointsFilter.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    def "EndpointsFilter is loaded if micronaut.security.enabled does not exists"() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.security.enabled': false])

        when:
        context.getBean(EndpointsFilter.class)

        then:
        noExceptionThrown()

        cleanup:
        context.close()
    }

    def "EndpointsFilter is not loaded if micronaut.security.enabled=true"() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.security.enabled': true])

        when:
        context.getBean(EndpointsFilter.class)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }
}
