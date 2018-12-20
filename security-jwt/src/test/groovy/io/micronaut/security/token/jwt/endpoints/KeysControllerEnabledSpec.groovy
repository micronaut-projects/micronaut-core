package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class KeysControllerEnabledSpec extends Specification {

    @Unroll("if m.s.enabled=true and m.s.token.jwt.enabled=true and m.s.token.jwt.endpoints.oauth.enabled=false bean [#description] is not loaded")
    void "if micronaut.security.enabled=false security related beans are not loaded"(Class clazz, String description) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                          : KeysControllerEnabledSpec.simpleName,
                'micronaut.security.enabled'                         : true,
                'micronaut.security.token.jwt.enabled'               : true,
                'micronaut.security.token.jwt.endpoints.keys.enabled': false,

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains('No bean of type [' + clazz.name + '] exists.')

        cleanup:
        embeddedServer.close()

        where:
        clazz << [
                KeysController,
                KeysControllerConfiguration,
                KeysControllerConfigurationProperties,
        ]

        description = clazz.name
    }

    @Unroll
    void "#description loads if micronaut.security.endpoints.keys.enabled=true"(Class clazz, String description) {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : KeysControllerEnabledSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.endpoints.keys.enabled': true,

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(clazz)

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()

        where:
        clazz << [KeysController, KeysControllerConfiguration, KeysControllerConfigurationProperties]
        description = clazz.name
    }
}
