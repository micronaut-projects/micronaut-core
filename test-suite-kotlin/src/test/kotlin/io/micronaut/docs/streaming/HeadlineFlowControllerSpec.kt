package io.micronaut.docs.streaming

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class HeadlineFlowControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        // tag::streamingClientWithFlow[]
        "test client annotation streaming with Flow" {
            val headlineClient = embeddedServer
                    .applicationContext
                    .getBean(HeadlineFlowClient::class.java)

            val headline = headlineClient.streamFlow().take(1).toList().first()

            headline shouldNotBe null
            headline.text shouldStartWith "Latest Headline"
        }
        // end::streamingClientWithFlow[]

        "test error route with Flow" {
            val ex = shouldThrowExactly<HttpClientResponseException> {
                client.toBlocking().exchange(HttpRequest.GET<Any>("/streaming/illegal"), String::class.java)
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }
    }
}
