package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.exceptions.ReadTimeoutException
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A read timeout of the JDK client elapses while the response is awaited, and says so.
 */
class JdkReadTimeoutPhaseSpec extends Specification {

    void "a #kind exchange that gets no response times out before the headers"() {
        given:
        ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        CountDownLatch requested = new CountDownLatch(1)
        Socket accepted = null
        Thread.startDaemon {
            try {
                accepted = serverSocket.accept()
                def reader = accepted.inputStream.newReader('ISO-8859-1')
                String line
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                }
                requested.countDown()
            } catch (IOException ignored) {
            }
        }
        URI uri = URI.create("http://127.0.0.1:${serverSocket.localPort}/")
        ApplicationContext ctx = ApplicationContext.run(['micronaut.http.client.read-timeout': '500ms'])

        when:
        def client = raw ? ctx.createBean(RawHttpClient) : ctx.createBean(HttpClient, uri.toURL())
        def exchange = raw
                ? Mono.from(((RawHttpClient) client).exchange(HttpRequest.GET(uri), null, null, RawRequestOptions.proxy()))
                : Mono.from(((HttpClient) client).exchange(HttpRequest.GET('/'), String))
        def pending = exchange.toFuture()
        assert requested.await(10, TimeUnit.SECONDS)
        pending.get(10, TimeUnit.SECONDS)

        then:
        def e = thrown(java.util.concurrent.ExecutionException)
        e.cause instanceof ReadTimeoutException
        !((ReadTimeoutException) e.cause).headersReceived

        cleanup:
        client?.close()
        ctx.close()
        accepted?.close()
        serverSocket.close()

        where:
        kind        | raw
        'raw'       | true
        'buffering' | false
    }
}
