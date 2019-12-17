package io.micronaut.docs.server.binding

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import java.util.ArrayList

class BindingControllerTest: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test cookie binding" {
            var body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/cookieName").cookie(Cookie.of("myCookie", "cookie value")))

            body shouldNotBe null
            body shouldBe "cookie value"

            body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/cookieInferred").cookie(Cookie.of("myCookie", "cookie value")))

            body shouldNotBe null
            body shouldBe "cookie value"
        }

        "test multiple cookie binding" {
            val cookies = HashSet<Cookie>()
            cookies.add(Cookie.of("myCookieA", "cookie A value"))
            cookies.add(Cookie.of("myCookieB", "cookie B value"))

            var body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/cookieMultiple").cookies(cookies))

            body shouldNotBe null
            body shouldBe "[\"cookie A value\",\"cookie B value\"]"
        }

        "test header binding"() {
            var body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/headerName").header("Content-Type", "test"))

            body shouldNotBe null
            body shouldBe "test"

            body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/headerInferred").header("Content-Type", "test"))

            body shouldNotBe null
            body shouldBe "test"

            val ex = shouldThrow<HttpClientResponseException> {
                client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/headerNullable"))
            }
            ex.response.status shouldBe HttpStatus.NOT_FOUND
        }

        "test header date binding"() {
            var body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/date").header("date", "Tue, 3 Jun 2008 11:05:30 GMT"))

            body shouldNotBe null
            body shouldBe "2008-06-03T11:05:30Z"

            body = client.toBlocking().retrieve(HttpRequest.GET<Any>("/binding/dateFormat").header("date", "03/06/2008 11:05:30 AM GMT"))

            body shouldNotBe null
            body shouldBe "2008-06-03T11:05:30Z[GMT]"
        }
    }
}
