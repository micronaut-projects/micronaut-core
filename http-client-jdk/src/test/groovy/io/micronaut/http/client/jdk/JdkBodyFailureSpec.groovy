package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import spock.lang.Specification

import java.net.http.HttpTimeoutException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow

/**
 * The failures of the streamed response bodies of the JDK client: a stream fails with a client
 * exception, a raw exchange with the failure of the JDK client, as it always did.
 */
class JdkBodyFailureSpec extends Specification {

    void "a body timeout of a client with a service id maps to a ReadTimeoutException"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.services.my-service.url': 'http://localhost:1'])
        DefaultJdkHttpClient client = ctx.getBean(DefaultJdkHttpClientRegistry).getClient(io.micronaut.http.client.HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_1_1), 'my-service', null)

        when:
        def mapped = client.mapReadFailure(new HttpTimeoutException('request timed out'))

        then: 'the shared exception is not decorated, a new one is'
        mapped instanceof ReadTimeoutException
        ((ReadTimeoutException) mapped).headersReceived
        mapped.serviceId == 'my-service'

        when:
        def again = client.mapReadFailure(new HttpTimeoutException('request timed out'))

        then:
        again instanceof ReadTimeoutException
        !again.is(mapped)

        cleanup:
        ctx.close()
    }

    void "the body of a stream fails with a client exception: #error"() {
        given:
        def subscriber = new ByteBodySubscriber(new BodySizeLimits(Long.MAX_VALUE, Long.MAX_VALUE), true, null)
        def body = subscribe(subscriber)

        when:
        def future = body.buffer()
        subscriber.onError(error)
        future.get()

        then:
        def e = thrown(ExecutionException)
        type.isInstance(e.cause)
        e.cause instanceof ReadTimeoutException || e.cause.cause.is(error)

        where:
        error                                     | type
        new IOException('connection reset')       | HttpClientException
        new HttpTimeoutException('timed out')     | ReadTimeoutException
    }

    void "the body of a raw exchange fails with the failure of the JDK client"() {
        given:
        def error = new IOException('connection reset')
        def subscriber = new ByteBodySubscriber(new BodySizeLimits(Long.MAX_VALUE, Long.MAX_VALUE), false, null)
        def body = subscribe(subscriber)

        when:
        def future = body.buffer()
        subscriber.onError(error)
        future.get()

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    private static subscribe(ByteBodySubscriber subscriber) {
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            void request(long n) {
                // the test signals the error itself, there is no data to request
            }

            @Override
            void cancel() {
                // nothing to release: the test owns no upstream
            }
        })
        return subscriber.body.toCompletableFuture().get()
    }
}
