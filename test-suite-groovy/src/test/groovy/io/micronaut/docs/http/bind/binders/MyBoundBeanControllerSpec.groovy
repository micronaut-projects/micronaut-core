package io.micronaut.docs.http.bind.binders

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Test
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.Assert.assertEquals
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
        Set cookiesSet = [Cookie.of("shoppingCart", "5"),
                          Cookie.of("displayName", "John Q Micronaut")]

        HttpRequest request = HttpRequest.POST("/customBinding/annotated", "{\"key\":\"value\"}")
                .cookies(cookiesSet)
                .header(HttpHeaders.AUTHORIZATION, "munaut:P@ssw0rd")
        HttpClientResponseException responseException = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(request, Argument.mapOf(String.class, String.class)))

        then:
        responseException.getMessage() == "Failed to convert argument [bean] for value [null] due to: Illegal base64 character 3a"

    }

    void testAnnotationBinding() {
        when:
        Set cookiesSet = [Cookie.of("shoppingCart", "5"),
                          Cookie.of("displayName", "John Q Micronaut")]
        HttpRequest request = HttpRequest.POST("/customBinding/annotated", "{\"key\":\"value\"}")
                .cookies(cookiesSet)
                .basicAuth("munaut", "P@ssw0rd")
        Map<String, String> body = client.toBlocking().retrieve(request, Argument.mapOf(String.class, String.class))

        then:
        body.get("userName") == "munaut"
        body.get("displayName") == "John Q Micronaut"
        body.get("shoppingCartSize") == "5"
        body.get("bindingType") == "ANNOTATED"
    }

    void testTypeBinding() {
        when:
        Set cookiesSet = [Cookie.of("shoppingCart", "5"),
                          Cookie.of("displayName", "John Q Micronaut")]
        HttpRequest request = HttpRequest.POST("/customBinding/typed", "{\"key\":\"value\"}")
                .cookies(cookiesSet)
                .basicAuth("munaut", "P@ssw0rd")
        Map<String, String> body = client.toBlocking().retrieve(request, Argument.mapOf(String.class, String.class))

        then:
        body.get("userName") == "munaut"
        body.get("displayName") == "John Q Micronaut"
        body.get("shoppingCartSize") == "5"
        body.get("bindingType") == "TYPED"
    }
}
