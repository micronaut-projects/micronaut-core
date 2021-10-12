package io.micronaut.runtime.event

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EventListenerSpec extends Specification {

    void "test implementing an interface with @EventListener"() {
        ApplicationContext ctx = ApplicationContext.run()
        EventListenerImpl impl = ctx.getBean(EventListenerImpl)

        expect:
        !impl.called

        when:
        ctx.publishEvent(new MyEvent(""))

        then:
        impl.called

        cleanup:
        ctx.close()
    }
}
