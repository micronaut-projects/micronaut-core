package io.micronaut.security.token.propagation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.endpoints.LoginController
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import java.time.Duration

@Stepwise
class TokenPropagationSpec extends Specification {
    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    int booksPort

    @AutoCleanup
    @Shared
    EmbeddedServer booksEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient booksClient

    @Shared
    int inventoryPort

    @AutoCleanup
    @Shared
    EmbeddedServer inventoryEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient inventoryClient

    @AutoCleanup
    @Shared
    EmbeddedServer gatewayEmbeddedServer

    @AutoCleanup
    @Shared
    RxHttpClient gatewayClient


    def setupSpec() {

    }

    def "setup inventory server"() {
        given:
        inventoryPort = SocketUtils.findAvailableTcpPort()
        Map inventoryConfig = [
                'micronaut.server.port'                       : inventoryPort,
                (SPEC_NAME_PROPERTY)                          : 'tokenpropagation.inventory',
                'micronaut.security.token.jwt.enabled'                 : true,
                'micronaut.security.token.jwt.signatures.secret.validation.secret': 'pleaseChangeThisSecretForANewOne',
                'micronaut.security.enabled'                  : true,
        ]

        inventoryEmbeddedServer = ApplicationContext.run(EmbeddedServer, inventoryConfig, Environment.TEST)

        inventoryClient = inventoryEmbeddedServer.applicationContext.createBean(RxHttpClient, inventoryEmbeddedServer.getURL())

        when:
        for (Class beanClazz : [InventoryController]) {
            inventoryEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()
    }

    def "setup books server"() {
        given:
        booksPort = SocketUtils.findAvailableTcpPort()
        Map booksConfig = [
                'micronaut.server.port'                       : booksPort,
                (SPEC_NAME_PROPERTY)                          : 'tokenpropagation.books',
                'micronaut.security.token.jwt.enabled'                 : true,
                'micronaut.security.token.jwt.signatures.secret.validation.secret': 'pleaseChangeThisSecretForANewOne',
                'micronaut.security.enabled'                  : true,
        ]

        booksEmbeddedServer = ApplicationContext.run(EmbeddedServer, booksConfig, Environment.TEST)

        booksClient = booksEmbeddedServer.applicationContext.createBean(RxHttpClient, booksEmbeddedServer.getURL())

        when:
        for (Class beanClazz : [BooksController]) {
            booksEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()
    }

    def "setup gateway server"() {
        given:
        Map gatewayConfig = [
                (SPEC_NAME_PROPERTY): 'tokenpropagation.gateway',
                'micronaut.security.enabled': true,
                'micronaut.http.services.books.url': "http://localhost:${booksPort}",
                'micronaut.http.services.inventory.url': "http://localhost:${inventoryPort}",
                'micronaut.security.endpoints.login.enabled': true,
                'micronaut.security.token.propagation.enabled': true,
                'micronaut.security.token.propagation.service-id-regex': 'books|inventory',
                'micronaut.security.token.jwt.enabled'                 : true,
                'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne',
                'micronaut.security.token.writer.header.enabled': true,
        ]

        gatewayEmbeddedServer = ApplicationContext.run(EmbeddedServer, gatewayConfig, Environment.TEST)

        when:
        for (Class beanClazz : [
                BooksClient,
                InventoryClient,
                GatewayController,
                SampleAuthenticationProvider,
                LoginController,
        ]) {
            gatewayEmbeddedServer.applicationContext.getBean(beanClazz)
        }

        then:
        noExceptionThrown()


        when:
        def configuration = new DefaultHttpClientConfiguration()
        configuration.setReadTimeout(Duration.ofSeconds(30))
        gatewayClient = gatewayEmbeddedServer.applicationContext.createBean(RxHttpClient, gatewayEmbeddedServer.getURL(), configuration)

        then:
        noExceptionThrown()
    }

    def "verifies propagation works in a request which involves multiple services"() {
        when: 'attempt to login'
        def creds = new UsernamePasswordCredentials('sherlock', 'elementary')
        HttpResponse rsp = gatewayClient.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then: 'login works'
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken
        rsp.body().refreshToken

        when:
        String accessToken = rsp.body().accessToken
        List<Book> books = gatewayClient.toBlocking().retrieve(HttpRequest.GET("/api/gateway")
                .bearerAuth(accessToken), Argument.of(List, Book))

        then:
        noExceptionThrown()
        books
        books.size() == 2
    }
}
