package io.micronaut.security.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TokenPropagationHttpClientFilterEnabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.security.enabled': true,
            'micronaut.security.token.writer.header.enabled': true,
            'micronaut.security.token.propagation.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TokenPropagationHttpClientFilter is enabled if you set micronaut.security.token.jwt.propagation.enabled"() {
        when:
        context.getBean(TokenPropagationHttpClientFilter)

        then:
        noExceptionThrown()
    }
}