package io.micronaut.security.session

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SecuritySessionBeansWithSecurityDisabledSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                 : SecuritySessionBeansWithSecurityDisabledSpec.simpleName,
            'micronaut.security.enabled': false,
    ], Environment.TEST)

    @Unroll("if micronaut.security.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type ['+clazz.name+'] exists.')

        where:
        clazz << [
            SecuritySessionConfigurationProperties,
            SessionAuthenticationFetcher,
            SessionLoginHandler,
            SessionLogoutHandler,
            SessionSecurityFilterOrderProvider,
            SessionSecurityfilterRejectionHandler,
        ]

        description = clazz.name
    }
}
