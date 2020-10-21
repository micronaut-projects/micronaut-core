package io.micronaut.aop.targetaware

import io.micronaut.context.BeanContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class TargetAwareInterceptorSpec extends Specification {
    @Shared @AutoCleanup BeanContext beanContext = BeanContext.run()

    void 'test target aware interceptors'() {
        given:
        def bean1 = beanContext.getBean(TestBean)
        def bean2 = beanContext.getBean(AnotherBean)
        def interceptor1 = beanContext.getBean(TestTargetAwareInterceptor)
        def interceptor2 = beanContext.getBean(TypeSpecificTargetAwareInterceptor)

        expect:
        interceptor1.target
        interceptor1.count == 2
        interceptor2.target
        interceptor2.count == 1
    }
}
