package io.micronaut.runtime.event.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ShutdownEvent
import io.micronaut.context.event.StartupEvent
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Singleton

class EventListenerOverloadingSpec extends AbstractTypeElementSpec {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1067')
    void "method overloading should work for event listeners - java"() {
        when:
        def ctx = ApplicationContext.run()
        OverloadedListener listener = ctx.getBean(OverloadedListener)

        then:
        listener.startup
        !listener.shutdown

        when:
        ctx.stop()

        then:
        listener.startup
        listener.shutdown

    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1067')
    void "method overloading should work for event listeners - groovy"() {
        when:
        def ctx = ApplicationContext.run()
        GroovyOverloadedListener listener = ctx.getBean(GroovyOverloadedListener)

        then:
        listener.startup
        !listener.shutdown

        when:
        ctx.stop()

        then:
        listener.startup
        listener.shutdown

    }

    void "test parse listener"() {
        when:
        def definition = buildBeanDefinition('test.TestListener', '''
package test;
import io.micronaut.context.event.ShutdownEvent;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.*;
import javax.inject.Singleton;

@Singleton
class TestListener {

    StartupEvent startup;
    ShutdownEvent shutdown;

    @EventListener
    void receive(StartupEvent event) {
        this.startup = event;
    }

    @EventListener
    void receive(ShutdownEvent event) {
        this.shutdown = event;
    }
}

''')

        then:
        definition != null
        definition.executableMethods.size() == 2
        definition.executableMethods[0].arguments[0].type == StartupEvent

    }

    @Singleton
    static class GroovyOverloadedListener {

        StartupEvent startup;
        ShutdownEvent shutdown;

        @EventListener
        void receive(StartupEvent event) {
            this.startup = event;
        }

        @EventListener
        void receive(ShutdownEvent event) {
            this.shutdown = event;
        }
    }

}
