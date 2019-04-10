package io.micronaut.security.token.jwt.signature.secret

import io.micronaut.context.ApplicationContext
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration
import spock.lang.Specification

class SecretSignatureFactorySpec extends Specification {

    void "secret configuration triggers the creation of both SignatureGeneratorConfiguration and SignatureConfiguration"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne'
        ])

        expect:
        applicationContext.containsBean(SignatureGeneratorConfiguration)
        applicationContext.containsBean(SignatureConfiguration)

        and:
        applicationContext.getBeansOfType(SignatureGeneratorConfiguration).size() == 1
        applicationContext.getBeansOfType(SignatureConfiguration).size() == 1

        cleanup:
        applicationContext.close()
    }

    void "by default no SignatureGeneratorConfiguration and SignatureConfiguration bean exist"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
        ])

        expect:
        !applicationContext.containsBean(SignatureGeneratorConfiguration)
        !applicationContext.containsBean(SignatureConfiguration)

        cleanup:
        applicationContext.close()
    }
}
