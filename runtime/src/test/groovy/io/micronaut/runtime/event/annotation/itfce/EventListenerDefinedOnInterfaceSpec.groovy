package io.micronaut.runtime.event.annotation.itfce

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EventListenerDefinedOnInterfaceSpec extends Specification {

    void "test @EventListener defined on interface"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        DefaultThingService thingService = ctx.getBean(DefaultThingService)

        when:
        thingService.create("stuff")

        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.5)

        then:
        conditions.eventually {
            thingService.getThings().size() == 1
            thingService.getThings().contains("stuff")
        }


        cleanup:
        ctx.close()
    }
}
