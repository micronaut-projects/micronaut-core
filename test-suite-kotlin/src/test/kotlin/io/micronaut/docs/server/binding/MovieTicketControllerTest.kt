package io.micronaut.docs.server.binding

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.uri.UriTemplate
import io.micronaut.runtime.server.EmbeddedServer

class MovieTicketControllerTest : StringSpec() {
    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test bookmark controller" {
            var template = UriTemplate("/api/movie/ticket/terminator{?minPrice,maxPrice}")
            var uri = template.expand(mapOf("minPrice" to 5.0, "maxPrice" to 20.0))

            var response = client.toBlocking().exchange<Any>(uri)

            response.status shouldBe HttpStatus.OK
        }
    }
}