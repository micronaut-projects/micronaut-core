package io.micronaut.http.client.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.websocket.WebSocketConnectCancelSpec
import io.micronaut.websocket.context.WebSocketBeanRegistry
import org.jspecify.annotations.Nullable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A websocket connect reports the outcome of its handshake to the load balancer that selected its
 * instance, like an HTTP exchange.
 */
class WebSocketLoadBalancerSpec extends Specification {

    PollingConditions conditions = new PollingConditions(timeout: 10)

    void 'the reactive connect selects the instance when it is called, as before'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(URI.create('http://127.0.0.1:1'))
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when: 'connect is called without a subscription'
        client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')

        then:
        balancer.selections.get() == 1

        cleanup:
        client?.close()
        ctx?.close()
    }

    void 'a handshake that succeeds is reported as a success'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(true)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        def endpoint = Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).block(Duration.ofSeconds(10))

        then:
        endpoint != null
        balancer.outcomes == [LoadBalancer.Outcome.SUCCESS]

        cleanup:
        endpoint?.close()
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a handshake timeout is reported as a timeout and carries the service id'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(false)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def configuration = new DefaultHttpClientConfiguration()
        configuration.handshakeTimeout = Duration.ofSeconds(1)
        def client = client(ctx, balancer, configuration)

        when:
        Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof ReadTimeoutException
        ((ReadTimeoutException) e.cause).serviceId == 'raw'
        balancer.outcomes == [LoadBalancer.Outcome.TIMEOUT]

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a refused connection is reported as a connect failure'() {
        given:
        ServerSocket closed = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        int port = closed.localPort
        closed.close()
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(URI.create("http://127.0.0.1:$port"))
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)
        balancer.outcomes == [LoadBalancer.Outcome.CONNECT_FAILURE]

        cleanup:
        client?.close()
        ctx?.close()
    }

    void 'a response to the upgrade with status #status is reported as #outcome'() {
        given:
        def server = new RawResponseServer("HTTP/1.1 $status Nope\r\nContent-Length: 0\r\n\r\n")
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)
        balancer.outcomes == [outcome]

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()

        where:
        status | outcome
        400    | LoadBalancer.Outcome.SUCCESS
        503    | LoadBalancer.Outcome.SERVER_ERROR
    }

    void 'a connection the instance closes before it responds is reported as a reset'() {
        given:
        def server = new RawResponseServer(null)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        Mono.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).toFuture().get(10, TimeUnit.SECONDS)

        then:
        thrown(ExecutionException)
        balancer.outcomes == [LoadBalancer.Outcome.RESET]

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    void 'a cancelled connect releases the selection'() {
        given:
        def server = new WebSocketConnectCancelSpec.RawWebSocketServer(false)
        def ctx = ApplicationContext.run(['spec.name': 'WebSocketConnectCancelSpec'])
        def balancer = new RecordingLoadBalancer(server.uri)
        def client = client(ctx, balancer, new DefaultHttpClientConfiguration())

        when:
        def subscription = Flux.from(client.connect(WebSocketConnectCancelSpec.CancelClient, '/ws')).subscribe()
        server.awaitUpgradeRequest()
        subscription.dispose()

        then:
        server.awaitClosed()
        conditions.eventually {
            balancer.outcomes == [LoadBalancer.Outcome.CANCELLED]
        }

        cleanup:
        client?.close()
        ctx?.close()
        server?.close()
    }

    private static DefaultHttpClient client(ApplicationContext ctx, LoadBalancer balancer, DefaultHttpClientConfiguration configuration) {
        return DefaultHttpClient.builder()
            .loadBalancer(balancer)
            .configuration(configuration)
            .informationalServiceId('raw')
            .webSocketBeanRegistry(WebSocketBeanRegistry.forClient(ctx))
            .build()
    }

    static class RecordingLoadBalancer implements LoadBalancer {
        final List<LoadBalancer.Outcome> outcomes = new CopyOnWriteArrayList<>()
        final AtomicInteger selections = new AtomicInteger()
        final ServiceInstance instance

        RecordingLoadBalancer(URI uri) {
            instance = ServiceInstance.of('raw', uri)
        }

        @Override
        Publisher<ServiceInstance> select(@Nullable Object discriminator) {
            return Mono.fromSupplier {
                selections.incrementAndGet()
                instance
            }
        }

        @Override
        void report(ServiceInstance serviceInstance, LoadBalancer.Outcome outcome) {
            outcomes.add(outcome)
        }
    }

    /**
     * Reads the upgrade request, writes the given response, or nothing, and closes the connection.
     */
    static class RawResponseServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())

        RawResponseServer(@Nullable String response) {
            Thread.startDaemon('raw-response-server') {
                try (Socket socket = serverSocket.accept()) {
                    InputStream input = socket.inputStream
                    StringBuilder request = new StringBuilder()
                    while (!request.toString().endsWith('\r\n\r\n')) {
                        int b = input.read()
                        if (b < 0) {
                            return
                        }
                        request.append((char) b)
                    }
                    if (response != null) {
                        socket.outputStream.write(response.getBytes(StandardCharsets.US_ASCII))
                        socket.outputStream.flush()
                    }
                } catch (IOException ignored) {
                }
            }
        }

        URI getUri() {
            return URI.create("http://127.0.0.1:${serverSocket.localPort}")
        }

        @Override
        void close() {
            serverSocket.close()
        }
    }
}
