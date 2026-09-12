package io.micronaut.aop.adapter.classlevel

import io.micronaut.aop.Adapter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.ApplicationEventListener
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * The bean generated for an {@code @Adapter} advised method is a separate object that delegates to a method of
 * the bean declaring it. Class level advice on the declaring bean describes that bean, not the adapter, and
 * must not be applied to both.
 */
class ClassLevelAdviceOnAdaptedMethodSpec extends Specification {

    void 'class level advice is applied once, to the declaring bean, not to the generated adapter'() {
        given:
            LoggedInterceptor.reset()
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ClassLevelAdviceOnAdaptedMethodSpec'])

        when: 'the declaring bean and the adapter generated for its @EventListener method are both created'
            def bean = ctx.getBean(LoggedEventListenerBean)
            def adapter = ctx.getBeanDefinitions(ApplicationEventListener)
                    .find { it.stringValue(Adapter, 'adaptedBean').orElse(null) == LoggedEventListenerBean.name }

        then: 'the adapter exists but carries none of the declaring class advice'
            adapter != null
            !adapter.hasAnnotation(Logged)

        and: 'exactly one interceptor instance was created, for the declaring bean'
            LoggedInterceptor.INSTANCES.get() == 1

        when:
            ctx.publishEvent(new TheEvent())

        then: 'the event is delivered once and intercepted once, on the declaring bean'
            bean.received.size() == 1
            LoggedInterceptor.INSTANCES.get() == 1
            LoggedInterceptor.INVOCATIONS.size() == 1
            LoggedInterceptor.INVOCATIONS.first().endsWith('#onEvent')
            LoggedInterceptor.INVOCATIONS.first().contains('LoggedEventListenerBean')

        cleanup:
            ctx.close()
    }

    void 'advice declared on the adapted method still runs when the adapter delegates'() {
        given:
            LoggedInterceptor.reset()
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'MethodLevelAdviceOnAdaptedMethodSpec'])

        when:
            def bean = ctx.getBean(MethodAdvisedEventListenerBean)
            ctx.publishEvent(new TheEvent())

        then: 'the method advice is not lost, and is applied exactly once'
            bean.received.size() == 1
            LoggedInterceptor.INSTANCES.get() == 1
            LoggedInterceptor.INVOCATIONS.size() == 1
            LoggedInterceptor.INVOCATIONS.first().endsWith('#onEvent')

        cleanup:
            ctx.close()
    }

    void '@Scheduled generates no adapter, so class level advice is applied once'() {
        given:
            LoggedInterceptor.reset()
            ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ScheduledClassLevelAdviceSpec'])

        when:
            def bean = ctx.getBean(LoggedScheduledBean)

        then: 'the scheduled method runs and is intercepted on the declaring bean only'
            bean.latch.await(10, TimeUnit.SECONDS)
            LoggedInterceptor.INSTANCES.get() == 1
            LoggedInterceptor.INVOCATIONS.every { it.endsWith('#everyNow') }

        and: 'no adapter bean was generated for it'
            ctx.getAllBeanDefinitions().findAll { it.stringValue(Adapter, 'adaptedBean').orElse(null) == LoggedScheduledBean.name }.isEmpty()

        cleanup:
            ctx.close()
    }
}
