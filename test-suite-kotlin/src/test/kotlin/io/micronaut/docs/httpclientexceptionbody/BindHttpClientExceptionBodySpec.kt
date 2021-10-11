package io.micronaut.docs.httpclientexceptionbody

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer

class BindHttpClientExceptionBodySpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "spec.name" to BindHttpClientExceptionBodySpec::class.java.simpleName,
                "spec.lang" to "java"
            )
        )
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        //tag::test[]
        "after an httpclient exception the response body can be bound to a POJO" {
            try {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/books/1680502395"),
                        Argument.of(Book::class.java), // <1>
                        Argument.of(CustomError::class.java)) // <2>
            } catch (e: HttpClientResponseException) {
                e.response.status shouldBe HttpStatus.UNAUTHORIZED
            }
        }
        //end::test[]

        "exception binding error response" {
            try {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/books/1680502395"),
                        Argument.of(Book::class.java), // <1>
                        Argument.of(OtherError::class.java)) // <2>
            } catch (e: HttpClientResponseException) {
                e.response.status shouldBe HttpStatus.UNAUTHORIZED

                val jsonError = e.response.getBody(OtherError::class.java)

                jsonError shouldNotBe null
                jsonError.isPresent shouldNotBe true
            }
        }

        "verify bind error is thrown" {
            try {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/books/1491950358"),
                        Argument.of(Book::class.java),
                        Argument.of(CustomError::class.java))
            } catch (e: HttpClientResponseException) {
                e.response.status shouldBe HttpStatus.OK
                e.message!!.startsWith("Error decoding HTTP response body") shouldBe true
                e.message!!.contains("cannot deserialize from Object value") shouldBe true  // the jackson error
            }
        }
    }
}
