package io.micronaut.http.client

import io.micronaut.core.execution.DelayedExecutionFlow
import io.micronaut.core.execution.ExecutionFlow
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.ByteBodyHttpResponseWrapper
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpHeaders
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

    void "the hop-by-hop headers are stripped in place"() {
        given:
        MutableHttpHeaders headers = HttpRequest.GET("/").headers
        headers.add("Connection", "keep-alive, X-Hop")
        headers.add("Connection", "x-other-hop,")
        headers.add("X-Hop", "1")
        headers.add("X-Other-Hop", "2")
        headers.add("Keep-Alive", "timeout=5")
        headers.add("Proxy-Authorization", "Basic abc")
        headers.add("proxy-connection", "keep-alive")
        headers.add("TE", "trailers")
        headers.add("Trailer", "X-Checksum")
        headers.add("Transfer-Encoding", "chunked")
        headers.add("Upgrade", "h2c")
        headers.add("Accept", "text/plain")
        headers.add("Accept", "application/json")
        headers.add("X-Proxyish", "kept")

        when:
        RawHttpClientSupport.stripHopByHopHeaders(headers)

        then:
        headers.names().toList().sort() == ["Accept", "X-Proxyish"]
        headers.getAll("Accept") == ["text/plain", "application/json"]
    }

    void "a copied request leaves out the hop-by-hop headers only when asked to"() {
        given:
        MutableHttpRequest<?> request = HttpRequest.GET("http://gateway/items")
            .header("Host", "gateway")
            .header("Connection", "X-Hop")
            .header("X-Hop", "1")
            .header("Proxy-Authorization", "Basic abc")
            .header("Accept", "text/plain")
        request.headers.add("Accept", "application/json")
        request.setAttribute("attr", "value")

        when:
        MutableHttpRequest<?> stripped = RawHttpClientSupport.copyRequest(request, RawRequestOptions.builder().stripHopByHopHeaders(true).build())
        MutableHttpRequest<?> kept = RawHttpClientSupport.copyRequest(request, RawRequestOptions.builder().retainHostHeader(true).build())

        then:
        stripped.headers.names().toList().sort() == ["Accept", "Host"]
        stripped.headers.getAll("Accept") == ["text/plain", "application/json"]
        stripped.getAttribute("attr").get() == "value"
        kept.headers.names().toList().sort() == ["Accept", "Connection", "Host", "Proxy-Authorization", "X-Hop"]
        request.headers.names().size() == 5
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
