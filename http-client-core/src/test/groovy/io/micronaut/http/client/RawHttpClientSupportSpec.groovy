package io.micronaut.http.client

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpResponse
import groovy.transform.EqualsAndHashCode
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpRequestWrapper
import io.micronaut.http.ServerHttpRequest
import io.micronaut.http.body.ByteBody
import io.micronaut.http.body.DirectByteBodyAccess
import io.micronaut.http.simple.SimpleHttpRequest
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.body.CloseableByteBody
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.client.exceptions.ReadTimeoutException
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.ref.WeakReference
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

    void "a completed exchange does not stay reachable until the timeout elapses"() {
        given:
        WeakReference<Object> timed = completeWithLongTimeout()

        expect:
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            assert timed.get() == null
        }
    }

    void "a cancelled exchange does not stay reachable until the timeout elapses"() {
        given:
        WeakReference<Object> exchange = cancelWithLongTimeout()

        expect:
        new PollingConditions(timeout: 10).eventually {
            System.gc()
            assert exchange.get() == null
        }
    }

    private static WeakReference<Object> completeWithLongTimeout() {
        DelayedExecutionFlow<HttpResponse<?>> exchange = DelayedExecutionFlow.create()
        ExecutionFlow<HttpResponse<?>> timed = RawHttpClientSupport.withResponseTimeout(exchange, Duration.ofHours(1))
        HttpResponse<?> received = null
        timed.onComplete { r, e -> received = r }
        exchange.complete(HttpResponse.ok())
        assert received != null
        return new WeakReference<Object>(timed)
    }

    private static WeakReference<Object> cancelWithLongTimeout() {
        DelayedExecutionFlow<HttpResponse<?>> exchange = DelayedExecutionFlow.create()
        RawHttpClientSupport.withResponseTimeout(exchange, Duration.ofHours(1)).cancel()
        assert exchange.isCancelled()
        return new WeakReference<Object>(exchange)
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

    void "a body replaced by an equal but distinct object is not claimed"() {
        given:
        CloseableByteBody bytes = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("original".bytes)
        DirectRequest received = new DirectRequest(new Entity(1, "secret"), bytes)
        Entity redacted = new Entity(1, null)
        HttpRequest<?> replaced = new HttpRequestWrapper<Object>(received) {
            @Override
            Optional<Object> getBody() {
                return Optional.of(redacted)
            }
        }
        HttpRequest<?> unchanged = new HttpRequestWrapper<Object>(received) {}

        expect:
        redacted == received.body.get()
        RawHttpClientSupport.claimServerRequestBody(replaced) == null
        RawHttpClientSupport.claimServerRequestBody(unchanged) != null

        cleanup:
        bytes.close()
    }

    @EqualsAndHashCode(includes = "id")
    static class Entity {
        final int id
        final String secret

        Entity(int id, String secret) {
            this.id = id
            this.secret = secret
        }
    }

    static class DirectRequest extends SimpleHttpRequest<Object> implements ServerHttpRequest<Object>, DirectByteBodyAccess {
        private final CloseableByteBody bytes

        DirectRequest(Object body, CloseableByteBody bytes) {
            super(HttpMethod.POST, "/items", body)
            this.bytes = bytes
        }

        @Override
        ByteBody byteBody() {
            return bytes
        }

        @Override
        ByteBody byteBodyDirect() {
            return bytes
        }
    }
}
