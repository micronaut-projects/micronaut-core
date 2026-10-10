package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.AsyncHttpClient
import io.micronaut.http.client.AsyncStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

/**
 * An {@link AsyncHttpClient} is injected like an {@code HttpClient}: it is the async view of the
 * client the same injection point gets.
 */
class JdkAsyncHttpClientInjectionSpec extends Specification {
    static final String SPEC_NAME = 'JdkAsyncHttpClientInjectionSpec'
    static final int PORT = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name'                                : SPEC_NAME,
        'micronaut.server.port'                    : PORT,
        'micronaut.http.services.async-greeter.url': "http://localhost:$PORT".toString(),
    ])

    void "the async client bean exchanges"() {
        given:
        AsyncHttpClient client = server.applicationContext.getBean(AsyncHttpClient)

        expect:
        client.retrieve(server.URI.toString() + "/async-injection/hello").toCompletableFuture().get(10, TimeUnit.SECONDS) == "hello"
    }

    void "the async client beans are the views of the JDK client"() {
        expect:
        server.applicationContext.getBean(AsyncStreamingHttpClient).getClass().simpleName == 'DefaultAsyncHttpClient'
    }

    void "an async client injected with @Client resolves relative requests against its URL"() {
        given:
        InjectedClients clients = server.applicationContext.getBean(InjectedClients)

        expect:
        clients.root.retrieve("/async-injection/hello").toCompletableFuture().get(10, TimeUnit.SECONDS) == "hello"
        clients.service.exchange(HttpRequest.GET("/async-injection/hello"), String).toCompletableFuture().get(10, TimeUnit.SECONDS).body() == "hello"
    }

    @Singleton
    @Requires(property = "spec.name", value = "JdkAsyncHttpClientInjectionSpec")
    static class InjectedClients {
        @Inject
        @Client("/")
        AsyncHttpClient root

        @Inject
        @Client(id = "async-greeter")
        AsyncHttpClient service
    }

    @Controller("/async-injection")
    @Requires(property = "spec.name", value = "JdkAsyncHttpClientInjectionSpec")
    static class HelloController {
        @Get(value = "/hello", produces = "text/plain")
        String hello() {
            return "hello"
        }
    }
}
