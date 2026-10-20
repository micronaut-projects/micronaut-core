package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * A raw exchange with {@link RawRequestOptions#isRetryStaleConnection()} turned off leaves the
 * retries to the caller, e.g. a gateway: the client does not send an idempotent request again
 * when its reused connection was closed before any response, unlike an exchange with the
 * default options, see {@link StaleConnectionRetrySpec}.
 */
class StaleConnectionRawProxySpec extends Specification {

    static final RawRequestOptions NO_RETRY = RawRequestOptions.proxy().toBuilder().retryStaleConnection(false).build()

    @AutoCleanup
    StaleConnectionRetrySpec.RawServer server = new StaleConnectionRetrySpec.RawServer({ int connection, int requestOnConnection -> requestOnConnection == 0 })

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '10s'])

    @AutoCleanup
    RawHttpClient client = ctx.createBean(RawHttpClient)

    private int exchange(String path, RawRequestOptions options) {
        HttpResponse<?> response = Mono.from(client.exchange(HttpRequest.GET("http://127.0.0.1:${server.port}$path"), null, null, options)).block()
        try {
            return response.code()
        } finally {
            if (response instanceof AutoCloseable) {
                ((AutoCloseable) response).close()
            }
            // let the connection go back to the pool before the next request is sent
            Thread.sleep(100)
        }
    }

    void "an exchange that does not retry a closed connection is not sent again"() {
        expect:
        exchange('/first', NO_RETRY) == 200

        when:
        exchange('/second', NO_RETRY)

        then: "the exchange fails with the closed connection, and the request was sent once"
        thrown(Exception)
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second']
        server.connections.get() == 1
    }

    void "a proxy exchange with the default options is still sent again"() {
        expect:
        exchange('/first', RawRequestOptions.proxy()) == 200

        when:
        int status = exchange('/second', RawRequestOptions.proxy())

        then:
        status == 200
        server.requests*.toString() == ['0/0 GET /first', '0/1 GET /second', '1/0 GET /second']
    }

    void "the retry of a closed connection is on by default and can be turned off"() {
        expect:
        RawRequestOptions.getDefault().isRetryStaleConnection()
        RawRequestOptions.proxy().isRetryStaleConnection()
        !NO_RETRY.isRetryStaleConnection()
        NO_RETRY != RawRequestOptions.proxy()
    }
}
