package io.micronaut.http.server.netty.types

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Specification

import static io.micronaut.http.HttpHeaders.CACHE_CONTROL

class CacheControlSpec extends Specification {


    void "test default cache control"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                ("spec.name"): "FileTypeHandlerSpec"
        ])
        int serverPort = embeddedServer.getPort()
        URL server = embeddedServer.getURL()
        ApplicationContext applicationContext = embeddedServer.applicationContext
        HttpClient rxClient = applicationContext.createBean(HttpClient, server)

        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.header(CACHE_CONTROL) == "private, max-age=60"

        cleanup:
        applicationContext.close()
    }

    void "test cache control public - deprecated"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                ("spec.name"): "FileTypeHandlerSpec",
                "netty.responses.file.cache-control.public": true
        ])
        int serverPort = embeddedServer.getPort()
        URL server = embeddedServer.getURL()
        ApplicationContext applicationContext = embeddedServer.applicationContext
        HttpClient rxClient = applicationContext.createBean(HttpClient, server)

        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.header(CACHE_CONTROL) == "public, max-age=60"

        cleanup:
        applicationContext.close()
    }

    void "test cache control max-age - deprecated"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                ("spec.name"): "FileTypeHandlerSpec",
                "netty.responses.file.cache-seconds": 120
        ])
        int serverPort = embeddedServer.getPort()
        URL server = embeddedServer.getURL()
        ApplicationContext applicationContext = embeddedServer.applicationContext
        HttpClient rxClient = applicationContext.createBean(HttpClient, server)

        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.header(CACHE_CONTROL) == "private, max-age=120"

        cleanup:
        applicationContext.close()
    }

    void "test cache control public"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                ("spec.name"): "FileTypeHandlerSpec",
                "micronaut.server.netty.responses.file.cache-control.public": true
        ])
        int serverPort = embeddedServer.getPort()
        URL server = embeddedServer.getURL()
        ApplicationContext applicationContext = embeddedServer.applicationContext
        HttpClient rxClient = applicationContext.createBean(HttpClient, server)

        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.header(CACHE_CONTROL) == "public, max-age=60"

        cleanup:
        applicationContext.close()
    }

    void "test cache control max-age"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                ("spec.name"): "FileTypeHandlerSpec",
                "micronaut.server.netty.responses.file.cache-seconds": 120
        ])
        int serverPort = embeddedServer.getPort()
        URL server = embeddedServer.getURL()
        ApplicationContext applicationContext = embeddedServer.applicationContext
        HttpClient rxClient = applicationContext.createBean(HttpClient, server)

        when:
        def response = rxClient.toBlocking().exchange('/test/html', String)

        then:
        response.header(CACHE_CONTROL) == "private, max-age=120"

        cleanup:
        applicationContext.close()
    }
}
