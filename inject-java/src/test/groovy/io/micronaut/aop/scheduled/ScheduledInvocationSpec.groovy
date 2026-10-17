package io.micronaut.aop.scheduled

import io.micronaut.aop.ScheduledInvocation
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ExecutableMethod
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ScheduledInvocationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ScheduledInvocationSpec'])

    MarkerInterceptor interceptor = ctx.getBean(MarkerInterceptor)

    def setup() {
        interceptor.records.clear()
        interceptor.invocations.clear()
    }

    void "the chain of the scheduled invocation carries the method and the schedule"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)
        ExecutableMethod<MarkedBean, Integer> method = ctx.getBeanDefinition(MarkedBean).getRequiredMethod("run", int)
        AnnotationValue<?> schedule = AnnotationValue.builder("test.Schedule").member("value", "every-minute").build()

        when:
        int result = ScheduledInvocation.invoke(method, schedule, bean, 0)

        then:
        result == 0
        interceptor.invocations.size() == 1
        interceptor.invocations[0].method.is(method)
        interceptor.invocations[0].schedule.is(schedule)
    }

    void "only the scheduled invocation carries the attribute, not the calls it makes in turn"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)
        ExecutableMethod<MarkedBean, Integer> method = ctx.getBeanDefinition(MarkedBean).getRequiredMethod("run", int)

        when:
        int result = ScheduledInvocation.invoke(method, null, bean, 2)

        then: 'the first chain claims it; the recursive calls and the same-named method of another bean do not'
        result == 0
        interceptor.records == ['MarkedBean.run:true', 'MarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
        interceptor.invocations.size() == 1
        interceptor.invocations[0].schedule == null
    }

    void "a direct call does not carry the attribute"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)

        when:
        bean.run(1)

        then:
        interceptor.records == ['MarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
    }

    void "the attribute reaches a proxyTarget proxy whose chain targets the real bean"() {
        given:
        MarkedProxyTargetBean bean = ctx.getBean(MarkedProxyTargetBean)
        ExecutableMethod<MarkedProxyTargetBean, String> method = ctx.getBeanDefinition(MarkedProxyTargetBean).getRequiredMethod("tick", String)

        when:
        String scheduled = ScheduledInvocation.invoke(method, null, bean, "a")
        String direct = bean.tick("b")

        then:
        scheduled == "a"
        direct == "b"
        interceptor.records == ['MarkedProxyTargetBean.tick:true', 'MarkedProxyTargetBean.tick:false']
    }

    void "the scheduled invocation is bound to the calling thread only"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)
        ExecutableMethod<MarkedBean, Integer> method = ctx.getBeanDefinition(MarkedBean).getRequiredMethod("run", int)

        when:
        Thread.ofPlatform().start { ScheduledInvocation.invoke(method, null, bean, 0) }.join()
        bean.run(0)

        then:
        interceptor.records == ['MarkedBean.run:true', 'OtherMarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
    }
}
