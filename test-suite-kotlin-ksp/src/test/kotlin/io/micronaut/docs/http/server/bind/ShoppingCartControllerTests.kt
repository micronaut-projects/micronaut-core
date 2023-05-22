package io.micronaut.docs.http.server.bind

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions

class ShoppingCartControllerTest: StringSpec(){

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "test binding bad credentials" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/annotated")
                    .cookie(Cookie.of("shoppingCart", "{}"))

            val responseException = Assertions.assertThrows(HttpClientResponseException::class.java) {
                client.toBlocking().retrieve(request)
            }
            val embedded: Map<*, *> = responseException.response.getBody(Map::class.java).get().get("_embedded") as Map<*, *>
            val message = ((embedded.get("errors") as java.util.List<*>).get(0) as Map<*, *>).get("message")

            responseException shouldNotBe null
            message shouldBe "Required ShoppingCart [sessionId] not specified"
        }

        "test annotation binding" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/annotated")
                    .cookie(Cookie.of("shoppingCart", "{\"sessionId\":5}"))
            val response: String = client.toBlocking().retrieve(request, String::class.java)

            response shouldNotBe null
            response shouldBe "Session:5"
        }

        "test typed binding" {
            val request: HttpRequest<*> = HttpRequest.GET<Any>("/customBinding/typed")
                    .cookie(Cookie.of("shoppingCart", "{\"sessionId\": 5, \"total\": 20}"))
            val body: Map<String, Any> = client.toBlocking().retrieve(request, Argument.mapOf(String::class.java, Any::class.java))

            body shouldNotBe null
            body["sessionId"] shouldBe "5"
            body["total"] shouldBe 20
        }
    }
}
