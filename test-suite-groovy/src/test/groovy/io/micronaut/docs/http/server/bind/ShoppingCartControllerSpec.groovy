package io.micronaut.docs.http.server.bind

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ShoppingCartControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void testBindingBadCredentials() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/customBinding/annotated")
                .cookie(Cookie.of("shoppingCart", "{}")))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "Required ShoppingCart [sessionId] not specified"
    }

    void testAnnotationBinding() {
        when:
        String response = client.toBlocking().retrieve(HttpRequest.GET("/customBinding/annotated")
                .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5}")), String.class)

        then:
        response == "Session:5"
    }

    void testTypeBinding() {
        when:
        HttpRequest request = HttpRequest.GET("/customBinding/typed")
                .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}"))

        Map<String, Object> body = client.toBlocking().retrieve(request, Argument.mapOf(String.class, Object.class))

        then:
        body.sessionId == "5"
        body.total == 20
    }
}
