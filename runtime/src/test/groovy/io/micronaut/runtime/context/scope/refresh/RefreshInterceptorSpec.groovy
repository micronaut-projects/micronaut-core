package io.micronaut.runtime.context.scope.refresh

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification

class RefreshInterceptorSpec extends Specification {

    void "RefreshInterceptor bean is not loaded for function environment"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(Environment.FUNCTION)

        then:
        !ctx.containsBean(RefreshInterceptor)

        cleanup:
        ctx.close()
    }

    void "RefreshInterceptor bean is loaded by default"() {
        when:
        ApplicationContext ctx = ApplicationContext.run()

        then:
        ctx.containsBean(RefreshInterceptor)

        cleanup:
        ctx.close()
    }

    void "RefreshInterceptor bean is not loaded for android environment"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(Environment.ANDROID)

        then:
        !ctx.containsBean(RefreshInterceptor)

        cleanup:
        ctx.close()
    }
}
