package io.micronaut.docs.streaming

import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.shouldThrowExactly
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

class HeadlineFlowControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
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
                client.exchange(HttpRequest.GET<Any>("/streaming/illegal"), String::class.java).blockingFirst()
            }
            val body = ex.response.getBody(String::class.java).get()

            ex.status shouldBe HttpStatus.BAD_REQUEST
            body shouldBe "illegal.argument"
        }
    }
}
