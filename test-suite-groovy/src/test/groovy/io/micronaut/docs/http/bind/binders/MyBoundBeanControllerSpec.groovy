package io.micronaut.docs.http.bind.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import static org.junit.jupiter.api.Assertions.assertThrows

class MyBoundBeanControllerSpec extends Specification{
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void testBindingBadCredentials() {
        when:
        Set cookiesSet = [Cookie.of("shoppingCart", "{}")]

        HttpRequest request = HttpRequest.GET("/customBinding/annotated")
                .cookies(cookiesSet)

        HttpClientResponseException responseException = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(request))

        then:
        responseException.getMessage() == "Required MyBindingAnnotation [sessionId] not specified"

    }

    void testAnnotationBinding() {
        when:
        Set cookiesSet = [Cookie.of("shoppingCart", "{\"sessionId\": 5}")]
        HttpRequest request = HttpRequest.GET("/customBinding/annotated")
                .cookies(cookiesSet)

        String response = client.toBlocking().retrieve(request, String.class)

        then:
        response == "Session:5"
    }

    void testTypeBinding() {
        when:
        Set cookiesSet = [Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}")]

        HttpRequest request = HttpRequest.GET("/customBinding/typed")
                .cookies(cookiesSet)

        Map<String, String> body = client.toBlocking().retrieve(request, Argument.mapOf(String.class, String.class))

        then:
        body.sessionId == "5"
        body.total == "20"
    }
}
