package io.micronaut.retry.intercept

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.retry.annotation.CircuitBreaker
import jakarta.inject.Singleton
import spock.lang.Specification

class CircuitBreakerWatchSpec extends Specification {

    void "the circuit of a method that went is dropped"() {
        given:
        def context = ApplicationContext.run(["spec.name": "CircuitBreakerWatchSpec"])
        def bean = context.getBean(WatchedService)
        def interceptor = context.getBean(DefaultRetryInterceptor)

        when:
        bean.call()

        then:
        thrown(IllegalStateException)
        interceptor.circuitContexts() == 1

        when: "the method goes"
        def definition = context.getBeanDefinition(WatchedService)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])

        then:
        interceptor.circuitContexts() == 0

        cleanup:
        context.close()
    }

    @Requires(property = "spec.name", value = "CircuitBreakerWatchSpec")
    @Singleton
    static class WatchedService {
        @CircuitBreaker(attempts = "1", delay = "1ms")
        void call() {
            throw new IllegalStateException("down")
        }
    }
}
