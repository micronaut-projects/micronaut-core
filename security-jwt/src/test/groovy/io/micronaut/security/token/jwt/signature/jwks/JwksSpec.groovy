package io.micronaut.security.token.jwt.signature.jwks

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthenticationFailed
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.endpoints.JwkProvider
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Named
import javax.inject.Singleton
import java.security.Principal
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Stepwise
class JwksSpec extends Specification {

    static final String SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    int gatewayPort

    @AutoCleanup
    @Shared
    EmbeddedServer booksEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient booksClient

    @AutoCleanup
    @Shared
    EmbeddedServer gatewayEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient gatewayClient

    def setupSpec() {
    }

    def "setup gateway server"() {
        given:
        Map gatewayConfig = [
                (SPEC_NAME_PROPERTY)                           : 'jwks.gateway',
                'micronaut.security.enabled'                   : true,
                'micronaut.security.token.jwt.enabled'         : true,
                'micronaut.security.endpoints.login.enabled'   : true,
                'micronaut.security.endpoints.keys.enabled'    : true,
        ]

        gatewayEmbeddedServer = ApplicationContext.run(EmbeddedServer, gatewayConfig, Environment.TEST)
        gatewayPort = gatewayEmbeddedServer.port

        expect:
        gatewayPort > 0

        when:
        for (Class beanClazz : [
                RSAJwkProvider,
                JwkProvider,
                RSASignatureGeneratorConfiguration,
                AuthenticationProvider,
        ]) {
            gatewayEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()

        when:
        gatewayClient = gatewayEmbeddedServer.applicationContext.createBean(RxHttpClient, gatewayEmbeddedServer.getURL())

        then:
        noExceptionThrown()
    }

    def "setup books server"() {
        given:
        Map booksConfig = [
                (SPEC_NAME_PROPERTY)                           : 'jwks.books',
                'micronaut.security.enabled'                   : true,
                'micronaut.security.token.jwt.enabled'         : true,
                'micronaut.security.token.jwt.signatures.jwks.gateway.url' : "http://localhost:${gatewayPort}/keys",
        ]

        booksEmbeddedServer = ApplicationContext.run(EmbeddedServer, booksConfig, Environment.TEST)

        booksClient = booksEmbeddedServer.applicationContext.createBean(RxHttpClient, booksEmbeddedServer.getURL())

        when:
        for (Class beanClazz : [SignatureConfiguration,
                                JwksSignatureConfigurationProperties,
                                JwksSignature,
                                HomeController]) {
            booksEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()
    }

    def "gateway exposes /keys"() {
        when:
        HttpResponse<Map> response = gatewayClient.toBlocking().exchange(HttpRequest.GET("/keys"),  Map)

        then:
        response.status == HttpStatus.OK

        and:
        response.body().containsKey('keys')
        response.body()['keys'].size() == 1
    }

    def "a JWT generated in gateway microservice can be validated in another service which consumes the JSON Web Key Set exposed by gateway /keys"() {
        when:
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials('user', 'password')
        HttpResponse rsp = gatewayClient.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken
        rsp.body().refreshToken

        when:
        String username = booksClient.toBlocking().retrieve(HttpRequest.GET('/').bearerAuth(rsp.body().accessToken), String)

        then:
        username == 'user'
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'jwks.books')
    @Controller("/")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    static class HomeController {

        @Produces(MediaType.TEXT_HTML)
        @Get("/")
        String username(Principal principal) {
            principal.name
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'jwks.gateway')
    static class AuthenticationProviderUserPassword implements AuthenticationProvider {

        @Override
        Publisher<AuthenticationResponse> authenticate(AuthenticationRequest authenticationRequest) {
            if ( authenticationRequest.identity == 'user' && authenticationRequest.secret == 'password' ) {
                return Flowable.just(new UserDetails('user', []))
            }
            return Flowable.just(new AuthenticationFailed())
        }
    }

    @Named("generator")
    @Singleton
    @Requires(property = 'spec.name', value = 'jwks.gateway')
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
