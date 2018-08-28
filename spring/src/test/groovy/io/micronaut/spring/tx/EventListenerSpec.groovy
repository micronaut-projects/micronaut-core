package io.micronaut.spring.tx

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EventListenerSpec extends Specification {

    void "test a transactional event listener is invoked once"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        TransactionalListener t = ctx.getBean(TransactionalListener)

        then:
        t.invokeCount() == 1

        cleanup:
        ctx.close()
    }
}
