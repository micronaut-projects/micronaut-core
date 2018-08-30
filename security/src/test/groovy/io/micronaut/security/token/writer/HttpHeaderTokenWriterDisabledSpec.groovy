package io.micronaut.security.token.writer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.security.token.propagation.TokenPropagationHttpClientFilter
import io.micronaut.security.token.writer.HttpHeaderTokenWriter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpHeaderTokenWriterDisabledSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup ApplicationContext context = ApplicationContext.run([
            'micronaut.security.enabled': true,
            (SPEC_NAME_PROPERTY):getClass().simpleName
    ], Environment.TEST)

    void "JwtHttpClientFilter is disabled by default"() {
        when:
        context.getBean(HttpHeaderTokenWriter)

        then:
        thrown(NoSuchBeanException)
    }
}