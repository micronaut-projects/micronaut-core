package io.micronaut.docs.session

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import kotlin.test.assertNotNull

class ShoppingControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        "testSessionValueUsedOnReturnValue" {
            // tag::view[]
            var response = Flux.from(client.exchange(HttpRequest.GET<Cart>("/shopping/cart"), Cart::class.java)) // <1>
                                 .blockFirst()
            var cart = response.body()

            assertNotNull(response.header(HttpHeaders.AUTHORIZATION_INFO)) // <2>
            assertNotNull(cart)
            cart.items.isEmpty()
            // end::view[]

            // tag::add[]
            val sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO) // <1>

            response = Flux.from(client.exchange(HttpRequest.POST("/shopping/cart/Apple", "")
                             .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart::class.java)) // <2>
                             .blockFirst()
            cart = response.body()
            // end::add[]

            assertNotNull(cart)
            cart.items.size shouldBe 1

            response = Flux.from(client.exchange(HttpRequest.GET<Any>("/shopping/cart")
                             .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart::class.java))
                             .blockFirst()
            cart = response.body()

            response.header(HttpHeaders.AUTHORIZATION_INFO)
            assertNotNull(cart)

            cart.items.size shouldBe 1
            cart.items[0] shouldBe "Apple"
        }
    }
}
