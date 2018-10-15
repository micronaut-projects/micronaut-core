package io.micronaut.security.token.jwt.cookie

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.bearer.AccessRefreshTokenLoginHandler
import io.micronaut.security.token.jwt.bearer.BearerTokenConfigurationProperties
import io.micronaut.security.token.jwt.bearer.BearerTokenReader
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CookieEnabledSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : CookieEnabledSpec.simpleName,
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.cookie.enabled': false,

    ], Environment.TEST)

    @Unroll("if micronaut.security.enabled=true and m.s.token.jwt.enabled=true and m.s.token.jwt.cookie.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type ['+clazz.name+'] exists.')

        where:
        clazz << [
                JwtCookieClearerLogoutHandler,
                JwtCookieConfigurationProperties,
                JwtCookieLoginHandler,
                JwtCookieTokenReader,
        ]

        description = clazz.name
    }

}
