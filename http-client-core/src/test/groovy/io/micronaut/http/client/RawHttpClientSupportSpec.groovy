package io.micronaut.http.client

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpResponse
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.client.exceptions.ReadTimeoutException
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration

class RawHttpClientSupportSpec extends Specification {

    void "cancelling a flow with a response timeout cancels the exchange and closes a late response"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> exchange = DelayedExecutionFlow.create()
        ExecutionFlow<HttpResponse<?>> timed = RawHttpClientSupport.withResponseTimeout(exchange, Duration.ofMinutes(1))
        def late = response()

        when:
        timed.cancel()

        then:
        exchange.isCancelled()

        when:
        exchange.complete(late.response)

        then:
        late.closed()
    }

    void "a response that arrives after the timeout is closed"() {
        given:
        DelayedExecutionFlow<HttpResponse<?>> exchange = DelayedExecutionFlow.create()
        ExecutionFlow<HttpResponse<?>> timed = RawHttpClientSupport.withResponseTimeout(exchange, Duration.ofMillis(50))
        Throwable failure = null
        timed.onComplete { r, e -> failure = e }
        def late = response()

        expect:
        new PollingConditions(timeout: 5).eventually {
            assert failure instanceof ReadTimeoutException
            assert exchange.isCancelled()
        }

        when:
        exchange.complete(late.response)

        then:
        late.closed()
    }

    private static Map response() {
        boolean closed = false
        CloseableByteBody body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("late".bytes)
        ByteBodyHttpResponse<?> response = ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok(), new CloseableByteBody() {
            @Delegate(excludes = ['close'])
            CloseableByteBody delegate = body

            @Override
            void close() {
                closed = true
                delegate.close()
            }
        })
        return [response: response, closed: { closed }]
    }
}
