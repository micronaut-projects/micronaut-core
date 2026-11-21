package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class MainApplicationSpec extends Specification {

    static final String[] ARGS = ["-micronaut.server.port=-1", "-spec.name=MainApplicationSpec"]

    void "routes in the main method"() {
        when:
        ApplicationContext context = MainApplication.start(ARGS)
        HttpClient client = client(context)

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/hello/World")) == "Hello World"

        cleanup:
        client?.close()
        context?.close()
    }

    void "routes that use beans"() {
        when:
        ApplicationContext context = MainApplication.startWithBeans(ARGS)
        HttpClient client = client(context)
        Item saved = client.toBlocking().retrieve(HttpRequest.POST("/items", new Item(0, "Pen")), Item)

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/items/" + saved.id()), Item).name() == "Pen"

        cleanup:
        client?.close()
        context?.close()
    }

    void "the chosen server with its configuration"() {
        when:
        ApplicationContext context = MainApplication.startOnNetty(ARGS)
        HttpClient client = client(context)

        then:
        client.toBlocking().retrieve(HttpRequest.GET("/ping")) == "pong"
        context.getBean(NettyHttpServerConfiguration).maxHeaderSize == 16384

        cleanup:
        client?.close()
        context?.close()
    }

    private static HttpClient client(ApplicationContext context) {
        context.createBean(HttpClient, context.getBean(EmbeddedServer).URL)
    }
}
