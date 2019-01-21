package io.micronaut.security.token.jwt.encryption.secret

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import spock.lang.Specification

class SecretEncryptionSpec extends Specification {

    void "SecretEncryption constructor does not raise exception if jwe algorithm and encryption method set are valid"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.encryptions.secret.generator.secret': 'XXX',
                'micronaut.security.token.jwt.encryptions.secret.generator.jwe-algorithm': 'dir',
                'micronaut.security.token.jwt.encryptions.secret.generator.encryption-method': 'A128CBC-HS256',
        ], Environment.TEST)

        when:
        ctx.getBean(SecretEncryptionFactory)

        then:
        noExceptionThrown()

        when:
        ctx.getBean(SecretEncryptionConfiguration)

        then:
        noExceptionThrown()

        when:
        EncryptionConfiguration encryptionConfiguration = ctx.getBean(EncryptionConfiguration)

        then:
        noExceptionThrown()

        encryptionConfiguration instanceof SecretEncryption

        cleanup:
        ctx.close()
    }
}
