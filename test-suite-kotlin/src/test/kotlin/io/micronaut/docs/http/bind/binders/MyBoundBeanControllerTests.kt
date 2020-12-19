package io.micronaut.docs.http.bind.binders

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions

class MyBoundBeanControllerTest: StringSpec(){

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )


    init {
        "test binding bad credentials" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/annotated")
                    .cookies(setOf(Cookie.of("shoppingCart", "{}")))

            val responseException = Assertions.assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().retrieve(request)
            }

            responseException shouldNotBe null
            responseException.message shouldBe "Required MyBindingAnnotation [sessionId] not specified"

        }

        "test annotation binding" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/annotated")
                    .cookies(setOf(Cookie.of("shoppingCart", "{\"sessionId\":5}")))
            val response: String = client.toBlocking().retrieve(request, String::class.java)

            response shouldNotBe null
            response shouldBe "Session:5"
        }

        "test typed binding" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/typed")
                    .cookies(setOf(Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}")))
            val body: Map<String, String> = client.toBlocking().retrieve(request, Argument.mapOf(String::class.java, String::class.java))

            body shouldNotBe null
            body["sessionId"] shouldBe "5"
            body["total"] shouldBe "20"
        }
    }
}
