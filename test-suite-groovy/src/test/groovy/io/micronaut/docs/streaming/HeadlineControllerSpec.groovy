package io.micronaut.docs.streaming

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxStreamingHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Maybe
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import static io.micronaut.http.HttpRequest.GET
import static java.util.concurrent.TimeUnit.SECONDS
import static org.junit.Assert.fail

class HeadlineControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    // tag::streamingClient[]
    void "test client annotation streaming"() throws Exception {
        when:
        def headlineClient = embeddedServer.applicationContext
                                           .getBean(HeadlineClient) // <1>

        Maybe<Headline> firstHeadline = headlineClient.streamHeadlines().firstElement() // <2>

        Headline headline = firstHeadline.blockingGet() // <3>

        then:
        headline
        headline.text.startsWith("Latest Headline")
    }
    // end::streamingClient[]

    void "test streaming client" () {
        when:
        RxStreamingHttpClient client = embeddedServer.applicationContext
                                                     .createBean(RxStreamingHttpClient, embeddedServer.URL)

        // tag::streaming[]
        Flowable<Headline> headlineStream = client.jsonStream(
                GET("/streaming/headlines"), Headline) // <1>
        CompletableFuture<Headline> future = new CompletableFuture<>() // <2>
        headlineStream.subscribe(new Subscriber<Headline>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1) // <3>
            }

            @Override
            void onNext(Headline headline) {
                println "Received Headline = $headline.text"
                future.complete(headline) // <4>
            }

            @Override
            void onError(Throwable t) {
                future.completeExceptionally(t) // <5>
            }

            @Override
            void onComplete() {
                // no-op // <6>
            }
        })
        // end::streaming[]

        then:
        try {
            Headline headline = future.get(3, SECONDS)
            assert headline.text.startsWith("Latest Headline")
        } catch (Throwable e) {
            fail("Asynchronous error occurred: ${e.message ?: e.getClass().simpleName}")
        }

        cleanup:
        client.stop()
    }
}
