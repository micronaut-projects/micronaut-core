package io.micronaut.runtime.event.annotation

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EventListenerSpec extends Specification {

    void "test listener is invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        AsyncListener a = ctx.getBean(AsyncListener)
        PollingConditions conditions = new PollingConditions(timeout: 1)

        then:
        conditions.eventually {
            a.completableInvoked
        }

        cleanup:
        ctx.close()
    }
}
