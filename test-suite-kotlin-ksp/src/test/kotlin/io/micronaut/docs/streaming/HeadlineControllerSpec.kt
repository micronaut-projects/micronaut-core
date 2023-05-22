package io.micronaut.docs.streaming

import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest.GET
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.Assert.fail
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HeadlineControllerSpec: StringSpec() {

    val embeddedServer = autoClose(
        ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
        embeddedServer.applicationContext.createBean(HttpClient::class.java, embeddedServer.url)
    )

    init {
        // tag::streamingClient[]
        "test client annotation streaming" {
            val headlineClient = embeddedServer
                    .applicationContext
                    .getBean(HeadlineClient::class.java) // <1>

            val firstHeadline = headlineClient.streamHeadlines().next() // <2>

            val headline = firstHeadline.block() // <3>

            headline shouldNotBe null
            headline.text shouldStartWith "Latest Headline"
        }
        // end::streamingClient[]

        "test streaming client" {
            val client = embeddedServer.applicationContext.createBean(
                StreamingHttpClient::class.java, embeddedServer.url)

            // tag::streaming[]
            val headlineStream = client.jsonStream(
                GET<Any>("/streaming/headlines"), Headline::class.java) // <1>
            val future = CompletableFuture<Headline>() // <2>
            headlineStream.subscribe(object : Subscriber<Headline> {
                override fun onSubscribe(s: Subscription) {
                    s.request(1) // <3>
                }

                override fun onNext(headline: Headline) {
                    println("Received Headline = ${headline.text!!}")
                    future.complete(headline) // <4>
                }

                override fun onError(t: Throwable) {
                    future.completeExceptionally(t) // <5>
                }

                override fun onComplete() {
                    // no-op // <6>
                }
            })
            // end::streaming[]

            try {
                val headline = future.get(3, TimeUnit.SECONDS)
                headline.text shouldStartWith "Latest Headline"
            } catch (e: Throwable) {
                fail("Asynchronous error occurred: " + (e.message ?: e.javaClass.simpleName))
            }
            client.stop()
        }
    }
}
