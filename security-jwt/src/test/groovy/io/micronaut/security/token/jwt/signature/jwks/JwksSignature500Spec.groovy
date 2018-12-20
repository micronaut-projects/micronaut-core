package io.micronaut.security.token.jwt.signature.jwks

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.JwtFixture
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwksSignature500Spec extends Specification implements JwtFixture {

    static final String SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY) : 'jwkssignature500spec',
            // need to turn this on due to io.micronaut.security.token.jwt.package-info.java
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true

    ], Environment.TEST)

    void "if the remote JWKS endpoint throws 500, the JwksSignature handles it and it does not crash"() {
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
        jwksSignature.supportedAlgorithmsMessage() == 'No algorithms are supported'
        !jwksSignature.supports(JWSAlgorithm.RS256)

        and:
        SignedJWT signedJWT = generateSignedJWT()
        !jwksSignature.verify(signedJWT)

        and:
        noExceptionThrown()

        when:
        FooController fooController = embeddedServer.applicationContext.getBean(FooController)

        then:
        noExceptionThrown()

        and: // calls the JWKS endpoint several times (first attempt and the configured number of attempts)
        fooController.called == 1 + jwksSignature.getRefreshJwksAttempts()

        cleanup:
        context.close()
    }

    @Requires(property = "spec.name", value = "jwkssignature500spec")
    @Controller("/keys")
    @Secured(SecurityRule.IS_ANONYMOUS)
    static class FooController {

        int called = 0

        @Get
        HttpResponse index() {
            called++
            HttpResponse.serverError()
        }
    }

}
