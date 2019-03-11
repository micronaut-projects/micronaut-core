package io.micronaut.security.token.jwt.signature.jwks

import com.nimbusds.jose.jwk.KeyType
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwksSignatureConfigurationSpec extends Specification {

    @Shared
    Map<String, Object> conf = [
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.jwks.opes.url': "http://localhost:8081/keys",
            'micronaut.security.token.jwt.signatures.jwks.opes.key-type': "EC",
    ]

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(conf)

    void "KeyType can be converted"() {
        when:
        JwksSignatureConfiguration jwksSignatureConfiguration = applicationContext.getBean(JwksSignatureConfiguration)

        then:
        noExceptionThrown()
        jwksSignatureConfiguration.keyType == KeyType.EC
    }
}
