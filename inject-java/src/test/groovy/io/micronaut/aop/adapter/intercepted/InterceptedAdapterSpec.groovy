package io.micronaut.aop.adapter.intercepted

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class InterceptedAdapterSpec extends AbstractTypeElementSpec {

    void 'test interceptor on an event'() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

        when:
            def service = ctx.getBean(MyBean)
            def interceptor = ctx.getBean(TransactionalEventInterceptor)

        then:
            interceptor.count == 0

        when:
            service.triggerEvent()

        then:
            service.count == 1
            interceptor.count == 1
            interceptor.executableMethod.name == "test"

        cleanup:
            ctx.close()
    }
}
