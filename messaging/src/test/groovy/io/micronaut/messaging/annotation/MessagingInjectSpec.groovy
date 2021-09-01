package io.micronaut.messaging.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.messaging.MessagingApplication
import spock.lang.Specification

class MessagingInjectSpec extends Specification {

    void "test the application can be injected"() {
        given:
        def ctx = ApplicationContext.run()

        when:
        SomeBean someBean = ctx.getBean(SomeBean)

        then:
        someBean.embeddedApplication instanceof MessagingApplication
    }
}
