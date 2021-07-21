package io.micronaut.session.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.session.Session
import io.micronaut.session.SessionStore
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import spock.lang.Specification

class SessionCreationSpec extends Specification {

    void "test a controller can create a session"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'spec.name': getClass().simpleName
        ])
        EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<?> response = client.toBlocking().exchange(HttpRequest.GET("/sessiontest"))

        then:
        response.header("SET-COOKIE").contains("SESSION")

        when:
        def sessionId = response.header(HttpHeaders.SET_COOKIE).split(';')[0].split('=')[1]
        String body = client.toBlocking()
                .retrieve(HttpRequest.GET("/sessiontest/get")
                        .cookie(Cookie.of("SESSION", sessionId)), String)

        then:
        body == "SessionCreationSpec"
    }

    @Requires(property = "spec.name", value = "SessionCreationSpec")
    @Controller('/sessiontest')
    static class SessionController {

        private final SessionStore<Session> sessionStore

        SessionController(SessionStore<Session> sessionStore) {
            this.sessionStore = sessionStore
        }

        @Get("/get")
        String getValue(Session session) {
            session.get("specName").get()
        }

        @Get(single = true)
        Publisher<MutableHttpResponse<?>> createSession(HttpRequest<?> request) {
            return Flux.create({ emitter ->
                Session session = SessionForRequest.find(request).orElseGet(() -> SessionForRequest.create(sessionStore, request));
                session.put("specName", "SessionCreationSpec")
                emitter.next(HttpResponse.ok())
                emitter.complete()
            }, FluxSink.OverflowStrategy.ERROR)
        }

    }
}
