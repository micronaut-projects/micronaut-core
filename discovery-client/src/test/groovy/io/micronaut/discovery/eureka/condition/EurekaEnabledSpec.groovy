package io.micronaut.discovery.eureka.condition

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import spock.lang.Specification

class EurekaEnabledSpec extends Specification {

    void "test client is not enabled with no config"() {
        def ctx = ApplicationContext.run()

        expect:
        !ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }

    void "test client is enabled with service discovery settings"() {
        def ctx = ApplicationContext.run(['eureka.client.discovery.use-secure-port': true])

        expect:
        ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }

    void "test client is enabled with service discovery enabled"() {
        def ctx = ApplicationContext.run(['eureka.client.discovery.enabled': true])

        expect:
        ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }

    void "test client is enabled with registration settings"() {
        def ctx = ApplicationContext.run(['eureka.client.registration.explicit-instance-id': true])

        expect:
        ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }

    void "test client is enabled with registration enabled"() {
        def ctx = ApplicationContext.run(['eureka.client.registration.enabled': true])

        expect:
        ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }

    void "test client is disabled if both registration and discovery are disabled"() {
        def ctx = ApplicationContext.run(['eureka.client.registration.enabled': false, 'eureka.client.discovery.enabled': false])

        expect:
        !ctx.containsBean(EurekaClient)

        cleanup:
        ctx.close()
    }
}
