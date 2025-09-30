package io.micronaut.kotlin.processing.aop.introduction

import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IntroductionOnConcreteClassSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run()

    void "test introduction of new interface on concrete class"() {
        when:
        ConcreteClass cc = applicationContext.getBean(ConcreteClass)
        def listenerAdviceInterceptor = applicationContext.getBean(ListenerAdviceInterceptor)

        then:
        cc instanceof ApplicationEventListener

        when:
        def event = new StartupEvent(applicationContext)
        cc.onApplicationEvent(event)

        then:
        listenerAdviceInterceptor.recievedMessages.contains(event)

        cleanup:
        listenerAdviceInterceptor.recievedMessages.clear()
    }
}
