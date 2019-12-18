package io.micronaut.docs.streaming

import io.kotlintest.matchers.string.shouldStartWith
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
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
    }
}
