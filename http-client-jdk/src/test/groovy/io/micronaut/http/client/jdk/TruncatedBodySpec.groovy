package io.micronaut.http.client.jdk

import io.micronaut.http.body.stream.BodySizeLimits
import io.micronaut.http.client.exceptions.ResponseClosedException
import spock.lang.Specification

import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow

/**
 * A response body that the JDK client reports as cut off by the connection fails with a
 * {@link ResponseClosedException} after the headers; any other error is passed on unchanged.
 */
class TruncatedBodySpec extends Specification {

    void "#error is a truncated body: #truncated"() {
        given:
        def subscriber = new ByteBodySubscriber(new BodySizeLimits(Long.MAX_VALUE, Long.MAX_VALUE))
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
        def body = subscriber.body.toCompletableFuture().get()

        when:
        def future = body.buffer()
        subscriber.onError(error)
        future.get()

        then:
        def e = thrown(ExecutionException)
        if (truncated) {
            assert e.cause instanceof ResponseClosedException
            assert ((ResponseClosedException) e.cause).headersReceived
        } else {
            assert e.cause.is(error)
        }

        where:
        error                                                                  | truncated
        new EOFException()                                                     | true
        new IOException("fixed content-length: 10, bytes received: 3")         | true
        new IOException("chunked transfer encoding, state: READING_LENGTH")    | true
        new IOException("connection reset")                                   | false
        new IOException()                                                      | false
        new IllegalStateException("fixed content-length: 10")                  | false
    }
}
