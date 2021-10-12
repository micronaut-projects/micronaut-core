package io.micronaut.docs.server.exception

import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class ExceptionHandlerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to ExceptionHandlerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.getURL())
    )

    init {
        "test exception is handled"() {
            val request = HttpRequest.GET<Any>("/books/stock/1234")
            val errorType = Argument.mapOf(
                String::class.java,
                Any::class.java
            )
            val ex = shouldThrow<HttpClientResponseException> {
                client!!.toBlocking().retrieve(request, Argument.LONG, errorType)
            }

            val response = ex.response
            val embedded: Map<*, *> = response.getBody(Map::class.java).get().get("_embedded") as Map<*, *>
            val message = ((embedded.get("errors") as java.util.List<*>).get(0) as Map<*, *>).get("message")

            response.status().shouldBe(HttpStatus.BAD_REQUEST)
            message shouldBe("No stock available")
        }
    }
}
