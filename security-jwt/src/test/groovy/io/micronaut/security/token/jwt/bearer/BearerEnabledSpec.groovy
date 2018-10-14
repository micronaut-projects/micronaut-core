package io.micronaut.security.token.jwt.bearer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class BearerEnabledSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : BearerEnabledSpec.simpleName,
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.bearer.enabled': false,

    ], Environment.TEST)

    @Unroll("if micronaut.security.enabled=true and m.s.token.jwt.enabled=true and m.s.token.jwt.bearer.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type ['+clazz.name+'] exists.')

        where:
        clazz << [
                AccessRefreshTokenLoginHandler,
                BearerTokenConfigurationProperties,
                BearerTokenReader,
        ]

        description = clazz.name
    }

}
