package io.micronaut.docs.http.bind.binders

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpHeaders
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
            val request: HttpRequest<*> = HttpRequest.POST("/customBinding/annotated", "{\"key\":\"value\"}")
                    .cookies(setOf(Cookie.of("shoppingCart", "5"),
                            Cookie.of("displayName", "John Q Micronaut")))
                    .header(HttpHeaders.AUTHORIZATION, "munaut:P@ssw0rd")
            val responseException = Assertions.assertThrows(HttpClientResponseException::class.java) {
                client!!.toBlocking().retrieve(request, Argument.mapOf(String::class.java, Object::class.java))
            }

            responseException shouldNotBe null
            responseException.message shouldBe "Failed to convert argument [bean] for value [null] due to: Illegal base64 character 3a"

        }

        "test annotation binding" {
            val request: HttpRequest<*> = HttpRequest.POST("/customBinding/annotated", "{\"key\":\"value\"}")
                    .cookies(setOf(Cookie.of("shoppingCart", "5"),
                            Cookie.of("displayName", "John Q Micronaut")))
                    .basicAuth("munaut", "P@ssw0rd")
            val body: Map<String, String> = client!!.toBlocking().retrieve(request, Argument.mapOf(String::class.java, String::class.java))

            body shouldNotBe null
            body["userName"] shouldBe "munaut"
            body["displayName"] shouldBe "John Q Micronaut"
            body["shoppingCartSize"] shouldBe "5"
            body["bindingType"] shouldBe "ANNOTATED"
        }

        "test typed binding" {
            val request: HttpRequest<*> = HttpRequest.POST("/customBinding/typed", "{\"key\":\"value\"}")
                    .cookies(setOf(Cookie.of("shoppingCart", "5"),
                            Cookie.of("displayName", "John Q Micronaut")))
                    .basicAuth("munaut", "P@ssw0rd")
            val body: Map<String, String> = client!!.toBlocking().retrieve(request, Argument.mapOf(String::class.java, String::class.java))

            body shouldNotBe null
            body["userName"] shouldBe "munaut"
            body["displayName"] shouldBe "John Q Micronaut"
            body["shoppingCartSize"] shouldBe "5"
            body["bindingType"] shouldBe "TYPED"
        }
    }
}