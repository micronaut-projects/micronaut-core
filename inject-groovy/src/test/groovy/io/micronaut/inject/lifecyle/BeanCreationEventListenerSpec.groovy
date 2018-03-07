package io.micronaut.inject.lifecyle

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import spock.lang.Specification

import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
class BeanCreationEventListenerSpec extends Specification {

    void "test bean creation listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:
        B b= context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

    }

    @Singleton
    static class B {
        String name
    }

    static class ChildB extends B {
        B original

        ChildB(B original) {
            this.original = original
        }
    }

    @Singleton
    static class BCreationListener implements BeanCreatedEventListener<B> {

        @Override
        B onCreated(BeanCreatedEvent<B> event) {
            ChildB childB = new ChildB(event.bean)
            childB.name = "good"
            return childB
        }
    }
}
