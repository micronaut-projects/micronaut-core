package io.micronaut.http.server.netty.ssl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.ssl.SslConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/3255")
class DeprecatedSslConfigurationSpec extends Specification {

    @AutoCleanup
    InMemoryAppender appender = new InMemoryAppender(SslConfiguration)

    def setup() {
        appender.clear()
    }

    def "self signed certificate is generated for #type configuration #property"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'DeprecatedSslConfigurationSpec',
                'micronaut.server.ssl.port'                                : -1,
                'micronaut.server.ssl.enabled'                             : true,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                (property)                                                 : true,
        ], Environment.TEST)

        def client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/cert"), String)

        then:
        response.body() == 'true'

        and:
        !shouldLog || appender.events == ["[WARN] The configuration micronaut.ssl.build-self-signed is deprecated. Use micronaut.server.ssl.build-self-signed instead."]

        cleanup:
        client.close()
        embeddedServer.close()

        where:
        property                                 | shouldLog | type
        "micronaut.ssl.build-self-signed"        | true      | 'deprecated'
        "micronaut.ssl.buildSelfSigned"          | true      | 'deprecated'
        "micronaut.server.ssl.build-self-signed" | false     | 'moved'
        "micronaut.server.ssl.buildSelfSigned"   | false     | 'moved'
    }

    def "ssl port can be set with #type property #property"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'DeprecatedSslConfigurationSpec',
                (property)                                                 : -1,
                'micronaut.server.ssl.enabled'                             : true,
                'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
                'micronaut.server.ssl.build-self-signed'                   : true,
        ], Environment.TEST)

        when:
        def client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        then:
        embeddedServer.getURL().port != 8443

        when:
        def response = client.toBlocking().exchange(HttpRequest.GET("/cert"), String)

        then:
        response.body() == 'true'

        and:
        !shouldLog || appender.events == ["[WARN] The configuration micronaut.ssl.port is deprecated. Use micronaut.server.ssl.port instead."]

        cleanup:
        client.close()
        embeddedServer.close()

        where:
        property                    | shouldLog | type
        "micronaut.ssl.port"        | true      | 'deprecated'
        "micronaut.server.ssl.port" | false     | 'moved'
    }

    @Requires(property = 'spec.name', value = 'DeprecatedSslConfigurationSpec')
    @Controller("/cert")
    static class TestController {

        @Get('/')
        String test(HttpRequest<?> request) {
            return "${request.isSecure()}"
        }
    }
}
