package io.micronaut.runtime.event.annotation

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EventListenerSpec extends Specification {

    void "test listener is invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        when:
        TestListener t = ctx.getBean(TestListener)
        GroovyListener g = ctx.getBean(GroovyListener)

        then:
        t.invoked
        g.invoked

        cleanup:
        ctx.close()
    }
}
