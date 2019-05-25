package io.micronaut.event

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class RegisterNamedEventListenerSpec extends Specification {


    void "test register event listener singleton"() {

        given:
        ApplicationContext context = ApplicationContext.run()
        context.getBeansOfType(ApplicationEventListener)
        context.registerSingleton(ApplicationEventListener, new MyEventListener(), Qualifiers.byName("foo"))

        when:
        def listener = context.getBean(ApplicationEventListener, Qualifiers.byName("foo"))

        then:
        listener instanceof MyEventListener
        context.getBeansOfType(ApplicationEventListener).contains(listener)

        when:
        def event = new StartupEvent(context)
        context.publishEvent(event)

        then:
        listener.lastEvent.is(event)
    }


    static class MyEventListener implements ApplicationEventListener {

        Object lastEvent
        @Override
        void onApplicationEvent(Object event) {
            lastEvent = event
        }
    }
}
