package io.micronaut.security.token.jwt.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.security.token.jwt.ConfigurationFixture
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AuthenticationWithJwtGeneratorSpec extends Specification implements ConfigurationFixture {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(minimumConfig)

    void "AuthenticationWithJwtGenerator bean exists"() {
        expect:
        applicationContext.containsBean(DefaultJwtAuthenticationFactory)
        applicationContext.containsBean(JwtAuthenticationFactory)
    }
}
