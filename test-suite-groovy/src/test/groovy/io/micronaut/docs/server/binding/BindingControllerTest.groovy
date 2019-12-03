package io.micronaut.docs.server.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BindingControllerTest extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "test cookie binding"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieName").cookie(Cookie.of("myCookie", "cookie value")))

        then:
        body != null
        body == "cookie value"

        when:
        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieInferred").cookie(Cookie.of("myCookie", "cookie value")))

        then:
        body != null
        body == "cookie value"
    }

    void "test multiple cookie binding"() {
        setup:
        HashSet<Cookie> cookies = [Cookie.of("myCookieA", "cookie A value"), Cookie.of("myCookieB", "cookie B value")] as Set

        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/cookieMultiple").cookies(cookies))

        then:
        body != null
        body == "[\"cookie A value\",\"cookie B value\"]"
    }

    void "test header binding"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/headerName").header("Content-Type", "test"))

        then:
        body != null
        body == "test"

        when:
        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/headerInferred").header("Content-Type", "test"))

        then:
        body != null
        body == "test"

        when:
        client.toBlocking().retrieve(HttpRequest.GET("/binding/headerNullable"))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.status == HttpStatus.NOT_FOUND
    }

    void "test header date binding"() {
        when:
        String body = client.toBlocking().retrieve(HttpRequest.GET("/binding/date").header("date", "Tue, 3 Jun 2008 11:05:30 GMT"))

        then:
        body != null
        body == "2008-06-03T11:05:30Z"

        when:
        body = client.toBlocking().retrieve(HttpRequest.GET("/binding/dateFormat").header("date", "03/06/2008 11:05:30 AM GMT"))

        then:
        body != null
        body == "2008-06-03T11:05:30Z[GMT]"
    }
}
