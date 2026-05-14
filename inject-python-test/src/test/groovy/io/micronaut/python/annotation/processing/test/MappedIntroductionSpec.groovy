package io.micronaut.python.annotation.processing.test

import io.micronaut.aop.Interceptor
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.python.annotation.processing.test.introduction.mapped.ListenerAdviceInterceptor
import spock.lang.PendingFeature

class MappedIntroductionSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0074")
    void "test mapped introduction of event listener interface on concrete class"() {
        given:
        def context = buildContext('''\
from jakarta.inject import Singleton
import java

ListenerAdviceMarker = java.type("io.micronaut.python.annotation.processing.test.introduction.mapped.ListenerAdviceMarker")

@ListenerAdviceMarker()
@Singleton
class MyBeanWithMappedIntroduction:
    pass
''')
        context.registerBeanDefinition(
                RuntimeBeanDefinition.builder(new ListenerAdviceInterceptor())
                    .singleton(true)
                    .exposedTypes(ListenerAdviceInterceptor.class, Interceptor.class)
                    .build()
        )

        when:
        def beanType = context.classLoader.loadClass("python.MyBeanWithMappedIntroduction")
        def bean = context.getBean(beanType)
        def listenerAdviceInterceptor = context.getBean(ListenerAdviceInterceptor)

        then:
        bean instanceof ApplicationEventListener

        when:
        def event = new StartupEvent(context)
        bean.onApplicationEvent(event)

        then:
        listenerAdviceInterceptor.receivedMessages.contains(event)

        cleanup:
        context?.close()
    }
}
