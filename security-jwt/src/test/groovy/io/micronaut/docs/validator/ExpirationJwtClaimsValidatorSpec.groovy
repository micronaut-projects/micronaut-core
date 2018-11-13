package io.micronaut.docs.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.validator.ExpirationJwtClaimsValidator
import spock.lang.Specification

class ExpirationJwtClaimsValidatorSpec extends Specification {

    void "by default ExpirationJwtClaimsValidator is enabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : ExpirationJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(ExpirationJwtClaimsValidator)

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()
    }


    void "you can disable ExpirationJwtClaimsValidator if you set micronaut.security.token.jwt.claims-validators.expiration=false"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : ExpirationJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.claims-validators.expiration': false
        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(ExpirationJwtClaimsValidator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        embeddedServer.close()
    }
}
