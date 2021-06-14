package io.micronaut.docs.server.exception

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.ReactorHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class ExceptionHandlerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to ExceptionHandlerSpec::class.simpleName))
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(ReactorHttpClient::class.java, embeddedServer.getURL())
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
            val body = response.getBody(errorType).get() as Map<String, Any>

            response.status().shouldBe(HttpStatus.BAD_REQUEST)
            body["message"].shouldBe("No stock available")
        }
    }
}
