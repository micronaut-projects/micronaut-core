package io.micronaut.kotlin.processing.aop.hotswap

import io.micronaut.aop.HotSwappableInterceptedProxy
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class ProxyHotswapSpec extends Specification {

    void "test AOP setup attributes"() {
        given:
        BeanContext beanContext = new DefaultBeanContext().start()
        def newInstance = new HotswappableProxyingClass()

        when:
        HotswappableProxyingClass foo = beanContext.getBean(HotswappableProxyingClass)
        then:
        foo instanceof HotSwappableInterceptedProxy
        foo.interceptedTarget().getClass() == HotswappableProxyingClass
        foo.test("test") == "Name is changed"
        foo.test2("test") == "Name is test"
        foo.interceptedTarget().invocationCount == 2

        foo.swap(newInstance)
        foo.interceptedTarget().invocationCount == 0
        foo.interceptedTarget() != foo
        foo.interceptedTarget().is(newInstance)
    }
}
