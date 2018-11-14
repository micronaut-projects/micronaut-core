package io.micronaut.docs.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.validator.SubjectNotNullJwtClaimsValidator
import spock.lang.Specification

class SubjectNotNullJwtClaimsValidatorSpec extends Specification {

    void "by default SubjectNotNullJwtClaimsValidator is enabled"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : SubjectNotNullJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(SubjectNotNullJwtClaimsValidator)

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()
    }


    void "you can disable SubjectNotNullJwtClaimsValidator if you set micronaut.security.token.jwt.claims-validators.subject=false"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : SubjectNotNullJwtClaimsValidatorSpec.simpleName,
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.claims-validators.subject-not-null': false

        ], Environment.TEST)

        when:
        embeddedServer.applicationContext.getBean(SubjectNotNullJwtClaimsValidator)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        embeddedServer.close()
    }
}
