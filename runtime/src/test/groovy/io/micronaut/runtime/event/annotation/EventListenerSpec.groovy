package io.micronaut.runtime.event.annotation

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EventListenerSpec extends Specification {

    void "test listener is invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        TestListener t = ctx.getBean(TestListener)
        GroovyListener g = ctx.getBean(GroovyListener)
        AsyncListener a = ctx.getBean(AsyncListener)
        PollingConditions conditions = new PollingConditions(timeout: 1)

        then:
        !a.invoked
        t.invoked
        g.invoked

        conditions.eventually {
            a.invoked
        }

        cleanup:
        ctx.close()
    }
}
