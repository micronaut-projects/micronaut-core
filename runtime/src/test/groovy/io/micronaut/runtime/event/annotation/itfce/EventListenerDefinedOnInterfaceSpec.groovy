package io.micronaut.runtime.event.annotation.itfce

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EventListenerDefinedOnInterfaceSpec extends Specification {

    void "test @EventListener defined on inteface"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        DefaultThingService thingService = ctx.getBean(DefaultThingService)

        when:
        thingService.create("stuff")

        then:
        thingService.getThings().size() == 1
        thingService.getThings().contains("stuff")


        cleanup:
        ctx.close()
    }
}
