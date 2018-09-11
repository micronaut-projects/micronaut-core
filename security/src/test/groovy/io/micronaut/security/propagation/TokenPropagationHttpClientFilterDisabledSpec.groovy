package io.micronaut.security.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class TokenPropagationHttpClientFilterDisabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.security.enabled': true,
            'micronaut.security.token.writer.header.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "TokenPropagationHttpClientFilter is disabled by default"() {
        when:
        context.getBean(TokenPropagationHttpClientFilter)

        then:
        thrown(NoSuchBeanException)
    }
}