package io.micronaut.security.token.basicauth

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class BasicAuthTokenValidatorSpec extends Specification {

    def "BasicAuthTokenValidator not loaded unless security is turn on"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator not loaded if micronaut.security.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.security.enabled': false])

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator is loaded if micronaut.security.enabled=true"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['micronaut.security.enabled': true])

        expect:
        applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }

    def "BasicAuthTokenValidator is loaded if micronaut.security.token.basic-auth.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.basic-auth.enabled': false
        ])

        expect:
        !applicationContext.containsBean(BasicAuthTokenValidator)

        cleanup:
        applicationContext.close()
    }
}
