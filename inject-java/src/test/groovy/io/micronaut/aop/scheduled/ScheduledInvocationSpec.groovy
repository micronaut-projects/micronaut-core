package io.micronaut.aop.scheduled

import io.micronaut.aop.ScheduledInvocation
import io.micronaut.context.ApplicationContext
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
    }

    void "only the scheduled invocation is marked, not the calls it makes in turn"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)
        ExecutableMethod<MarkedBean, Integer> method = ctx.getBeanDefinition(MarkedBean).getRequiredMethod("run", int)

        when:
        int result = ScheduledInvocation.invoke(method, bean, 2)

        then: 'the first chain claims the marker; the recursive calls and the same-named method of another bean do not see it'
        result == 0
        interceptor.records == ['MarkedBean.run:true', 'MarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
    }

    void "a direct call is not marked"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)

        when:
        bean.run(1)

        then:
        interceptor.records == ['MarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
    }

    void "the marker reaches a proxyTarget proxy whose chain targets the real bean"() {
        given:
        MarkedProxyTargetBean bean = ctx.getBean(MarkedProxyTargetBean)
        ExecutableMethod<MarkedProxyTargetBean, String> method = ctx.getBeanDefinition(MarkedProxyTargetBean).getRequiredMethod("tick", String)

        when:
        String scheduled = ScheduledInvocation.invoke(method, bean, "a")
        String direct = bean.tick("b")

        then:
        scheduled == "a"
        direct == "b"
        interceptor.records == ['MarkedProxyTargetBean.tick:true', 'MarkedProxyTargetBean.tick:false']
    }

    void "the marker is bound to the calling thread only"() {
        given:
        MarkedBean bean = ctx.getBean(MarkedBean)
        ExecutableMethod<MarkedBean, Integer> method = ctx.getBeanDefinition(MarkedBean).getRequiredMethod("run", int)

        when:
        Thread.ofPlatform().start { ScheduledInvocation.invoke(method, bean, 0) }.join()
        bean.run(0)

        then:
        interceptor.records == ['MarkedBean.run:true', 'OtherMarkedBean.run:false', 'MarkedBean.run:false', 'OtherMarkedBean.run:false']
    }
}
