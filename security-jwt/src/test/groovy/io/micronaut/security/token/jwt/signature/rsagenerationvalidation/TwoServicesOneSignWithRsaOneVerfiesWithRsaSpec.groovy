package io.micronaut.security.token.jwt.signature.rsagenerationvalidation

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.SignedJWT
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.jwt.AuthorizationUtils
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import io.micronaut.security.token.jwt.signature.SignatureGeneratorConfiguration
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureConfiguration
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureFactory
import io.micronaut.security.token.jwt.signature.rsa.RSASignatureGeneratorConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.Duration

@Stepwise
class TwoServicesOneSignWithRsaOneVerfiesWithRsaSpec extends Specification implements AuthorizationUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TwoServicesOneSignWithRsaOneVerfiesWithRsaSpec.class)

    private final String SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    private RSAKey rsaJwk

    @Shared
    int booksPort

    @Shared
    EmbeddedServer booksEmbeddedServer

    @Shared
    RxHttpClient booksClient

    @Shared
    EmbeddedServer gatewayEmbeddedServer

    @Shared
    RxHttpClient gatewayClient

    def setupSpec() {
        try {
            this.rsaJwk = new RSAKeyGenerator(2048)
                    .keyID("123")
                    .generate()
        } catch (JOSEException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error(e.getMessage())
            }
        }
    }

    def cleanupSpec() {
        booksEmbeddedServer?.stop()
        booksEmbeddedServer?.close()

        booksClient?.stop()
        booksClient?.close()

        gatewayClient?.stop()
        gatewayClient?.close()

        gatewayEmbeddedServer?.stop()
        gatewayEmbeddedServer?.close()
    }

    def "setup books server"() {
        given:
        booksPort = SocketUtils.findAvailableTcpPort()
        Map booksConfig = [
                (SPEC_NAME_PROPERTY)                          : 'rsajwtbooks',
                'micronaut.server.port'                       : booksPort,
                'micronaut.security.enabled'                  : true,
                'micronaut.security.token.jwt.enabled'        : true,
        ]

        booksEmbeddedServer = ApplicationContext.run(EmbeddedServer, booksConfig, Environment.TEST)
        BooksRsaSignatureConfiguration bean = booksEmbeddedServer.applicationContext.createBean(BooksRsaSignatureConfiguration, rsaJwk)
        booksEmbeddedServer.applicationContext.registerSingleton(bean)

        booksClient = booksEmbeddedServer.applicationContext.createBean(RxHttpClient, booksEmbeddedServer.getURL())

        when:
        booksEmbeddedServer.applicationContext.getBean(SignatureGeneratorConfiguration)

        then:
        thrown(NoSuchBeanException)

        when:
        for (Class beanClazz : [
                BooksRsaSignatureConfiguration,
                BooksController,
                RSASignatureConfiguration,
                RSASignatureFactory,
                SignatureConfiguration
        ]) {
            booksEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()
    }

    def "setup gateway server"() {
        given:
        Map gatewayConfig = [
                (SPEC_NAME_PROPERTY)                        : 'rsajwtgateway',
                'micronaut.security.enabled'                : true,
                'micronaut.security.token.jwt.enabled'      : true,
                'micronaut.security.endpoints.login.enabled': true,
                'micronaut.http.services.books.url'         : "http://localhost:${booksPort}",
        ]

        gatewayEmbeddedServer = ApplicationContext.run(EmbeddedServer, gatewayConfig, Environment.TEST)
        GatewayRsaSignatureConfiguration bean  = gatewayEmbeddedServer.applicationContext.createBean(GatewayRsaSignatureConfiguration, rsaJwk)
        gatewayEmbeddedServer.applicationContext.registerSingleton(bean)

        when:
        for (Class beanClazz : [
                GatewayRsaSignatureConfiguration,
                AuthenticationProviderUserPassword,
                GatewayBooksController,
                BooksClient,
                RSASignatureGeneratorConfiguration,
                SignatureConfiguration,
                SignatureGeneratorConfiguration
        ]) {
            gatewayEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()
    }

    void "JWT generated with a RSASignatureGeneratorConfiguration can be verified in another service with a RSASignatureConfiguration "() {

        when:
        def configuration = new DefaultHttpClientConfiguration()
        configuration.setReadTimeout(Duration.ofSeconds(30))
        gatewayClient = gatewayEmbeddedServer.applicationContext.createBean(RxHttpClient, gatewayEmbeddedServer.getURL(), configuration)

        then:
        noExceptionThrown()

        when:
        String token = loginWith(client,'user', 'password')

        then:
        token
        !(JWTParser.parse(token) instanceof EncryptedJWT)
        JWTParser.parse(token) instanceof SignedJWT

        when:
        List<Book> books = gatewayClient.toBlocking().retrieve(HttpRequest.GET("/books").bearerAuth(token), Argument.of(List, Book))

        then:
        books
        books.size() == 1
    }

    @Override
    RxHttpClient getClient() {
        return gatewayClient
    }
}
