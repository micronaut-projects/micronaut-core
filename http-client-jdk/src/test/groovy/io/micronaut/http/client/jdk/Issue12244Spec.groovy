package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/12244")
class Issue12244Spec extends Specification {
    @Shared
    String host = Optional.ofNullable(System.getenv(Environment.HOSTNAME)).orElse(SocketUtils.LOCALHOST)

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'Issue12244Spec',
        'micronaut.ssl.enabled': true,
        'micronaut.server.ssl.buildSelfSigned': true,
        'micronaut.server.ssl.port': -1,
    ])

    @AutoCleanup
    ApplicationContext clientCtx = ApplicationContext.run([
        'spec.name': 'Issue12244Spec',
        'micronaut.http.services.myclient.url': server.getURL().toString(),
        'micronaut.http.services.myclient.ssl.enabled': true,
        'micronaut.http.services.myclient.ssl.insecure-trust-all-certificates': true,
    ])

    def 'named jdk client applies service ssl configuration'() {
        given:
        def client = clientCtx.getBean(HttpClientRegistry).getClient(HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1), 'myclient', null)

        expect:
        client.toBlocking().retrieve('/ssl') == 'Hello'
    }

    @Requires(property = 'spec.name', value = 'Issue12244Spec')
    @Controller('/')
    static class TestController {
        @Get('/ssl')
        String simple() {
            'Hello'
        }
    }
}
