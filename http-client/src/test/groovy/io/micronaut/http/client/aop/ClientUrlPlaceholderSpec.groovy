package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.PropertySource
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultNettyHttpClientRegistry
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ClientUrlPlaceholderSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ClientUrlPlaceholderSpec'])

    void "a client whose URL is a property placeholder follows a change of the property"() {
        given:
        ApplicationContext ctx = server.applicationContext
        ctx.environment.addPropertySource(PropertySource.of("client-url-a", ['client-url.target': "${server.URL}/client-url-a"], 1000))
        UrlPlaceholderClient client = ctx.getBean(UrlPlaceholderClient)

        expect:
        client.name() == 'a'
        client.name() == 'a'

        when:
        ctx.environment.addPropertySource(PropertySource.of("client-url-b", ['client-url.target': "${server.URL}/client-url-b"], 1001))

        then:
        client.name() == 'b'
        client.name() == 'b'
    }

    void "disposing the client of a placeholder URL closes the client it had before the property changed"() {
        given:
        ApplicationContext ctx = server.applicationContext
        ctx.environment.addPropertySource(PropertySource.of("client-url-dispose-a", ['client-url.target': "${server.URL}/client-url-a"], 2000))
        ctx.getBean(UrlPlaceholderClient)
        def metadata = ctx.getBeanDefinition(UrlPlaceholderClient).getRequiredMethod("name").annotationMetadata
        def registry = ctx.getBean(DefaultNettyHttpClientRegistry)
        def first = registry.getClient(metadata)

        when:
        ctx.environment.addPropertySource(PropertySource.of("client-url-dispose-b", ['client-url.target': "${server.URL}/client-url-b"], 2001))
        def second = registry.getClient(metadata)
        registry.disposeClient(metadata)

        then:
        !first.is(second)
        !first.running
        !second.running
    }

    void "a client with a fixed URL keeps its client"() {
        given:
        FixedUrlClient client = server.applicationContext.createBean(FixedUrlClient)

        expect:
        client.name() == 'a'
        client.name() == 'a'
    }

    @Requires(property = 'spec.name', value = 'ClientUrlPlaceholderSpec')
    @Client('${client-url.target}')
    static interface UrlPlaceholderClient {
        @Get('/name')
        String name()
    }

    @Requires(property = 'spec.name', value = 'ClientUrlPlaceholderSpec')
    @Client('/client-url-a')
    static interface FixedUrlClient {
        @Get('/name')
        String name()
    }

    @Requires(property = 'spec.name', value = 'ClientUrlPlaceholderSpec')
    @Controller('/client-url-a')
    static class FirstController {
        @Get('/name')
        String name() {
            'a'
        }
    }

    @Requires(property = 'spec.name', value = 'ClientUrlPlaceholderSpec')
    @Controller('/client-url-b')
    static class SecondController {
        @Get('/name')
        String name() {
            'b'
        }
    }
}
