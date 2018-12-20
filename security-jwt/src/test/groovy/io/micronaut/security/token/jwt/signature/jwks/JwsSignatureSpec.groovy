package io.micronaut.security.token.jwt.signature.jwks

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.JwtFixture
import io.micronaut.security.token.jwt.endpoints.JwkProvider
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Named
import javax.inject.Singleton
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

class JwsSignatureSpec extends Specification implements JwtFixture {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    Map conf = [
            (SPEC_NAME_PROPERTY)                           : 'jwssignaturespec',
            'micronaut.security.enabled'                   : true,
            'micronaut.security.token.jwt.enabled'         : true,
            'micronaut.security.endpoints.keys.enabled'    : true,
    ]

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = embeddedServer = ApplicationContext.run(EmbeddedServer, conf, Environment.TEST)

    void "JwsSignature does not verify a RSA256 signed JWT, which was generated with a different signature, even if both the JwsSiganture and the JWT support the same algorithm"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.security.enabled': true,
                'micronaut.security.token.jwt.enabled': true,
                'micronaut.security.token.jwt.signatures.jwks.awscognito.url':  "http://localhost:${embeddedServer.getPort()}/keys",
        ], Environment.TEST)

        when:
        Collection<JwksSignature> beans = context.getBeansOfType(JwksSignature)

        then:
        beans

        when:
        JwksSignature jwksSignature = beans[0]

        then:
        jwksSignature.supportedAlgorithmsMessage() == 'Only the RS256 algorithms are supported'
        jwksSignature.supports(JWSAlgorithm.RS256)

        when:
        SignedJWT signedJWT = generateSignedJWT()

        then:
        jwksSignature.supports(signedJWT.getHeader().getAlgorithm())
        !jwksSignature.verify(signedJWT)

        cleanup:
        context.close()
    }

    @Named("generator")
    @Singleton
    @Requires(property = 'spec.name', value = 'jwssignaturespec')
    static class RSAJwkProvider implements JwkProvider, RSASignatureGeneratorConfiguration {
        private RSAKey jwk

        private static final Logger LOG = LoggerFactory.getLogger(RSAJwkProvider.class)

        RSAJwkProvider() {

            String keyId = UUID.randomUUID().toString()
            try {
                this.jwk = new RSAKeyGenerator(2048)
                        .algorithm(JWSAlgorithm.RS256)
                        .keyUse(KeyUse.SIGNATURE) // indicate the intended use of the key
                        .keyID(keyId) // give the key a unique ID
                        .generate()

            } catch (JOSEException e) {

            }
        }

        @Override
        JWK retrieveJsonWebKey() {
            return jwk
        }

        @Override
        RSAPrivateKey getPrivateKey() {
            try {
                return jwk.toRSAPrivateKey()
            } catch (JOSEException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("JOSEException getting RSA private key", e)
                }
            }
            return null
        }

        @Override
        JWSAlgorithm getJwsAlgorithm() {
            if (jwk.getAlgorithm() instanceof JWSAlgorithm) {
                return (JWSAlgorithm) jwk.getAlgorithm()
            }
            return null
        }

        @Override
        RSAPublicKey getPublicKey() {
            try {
                return jwk.toRSAPublicKey()
            } catch (JOSEException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("JOSEException getting RSA public key", e)
                }
            }
            return null
        }
    }
}
