package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.CompletionStagePublishers
import io.micronaut.discovery.AsyncDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.exceptions.NoAvailableServiceException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The JDK client selects the instance with {@link io.micronaut.http.client.AsyncLoadBalancer#selectAsync},
 * which a discovery client completes asynchronously, and cancels the selection with the request.
 */
class JdkLoadBalancerSelectAsyncSpec extends Specification {

    static final String SPEC = 'JdkLoadBalancerSelectAsyncSpec'

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': SPEC])

    HttpClient client() {
        ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, 'pending-service', null)
    }

    void awaitLookup(PendingDiscoveryClient discoveryClient) {
        new PollingConditions(timeout: 5).eventually {
            assert discoveryClient.futures.size() == 1
        }
    }

    def 'the request waits for the asynchronous selection'() {
        given:
        PendingDiscoveryClient discoveryClient = ctx.getBean(PendingDiscoveryClient)
        def response = Mono.from(client().retrieve(HttpRequest.GET('/jdk-select-async/hello'), String)).toFuture()
        awaitLookup(discoveryClient)

        expect:
        !response.isDone()
        discoveryClient.requested == ['pending-service']

        when:
        discoveryClient.futures[0].complete([ServiceInstance.of('pending-service', server.URI)])

        then:
        response.get() == 'hello'
    }

    def 'a cancelled request cancels the selection'() {
        given:
        PendingDiscoveryClient discoveryClient = ctx.getBean(PendingDiscoveryClient)

        when:
        Mono.from(client().retrieve(HttpRequest.GET('/jdk-select-async/hello'), String)).timeout(Duration.ofMillis(300)).block()

        then:
        thrown(Exception)
        new PollingConditions(timeout: 5).eventually {
            assert discoveryClient.futures.size() == 1
            assert discoveryClient.futures[0].isCancelled()
        }
    }

    def 'a selection without an available instance fails the request'() {
        given:
        PendingDiscoveryClient discoveryClient = ctx.getBean(PendingDiscoveryClient)
        def response = Mono.from(client().retrieve(HttpRequest.GET('/jdk-select-async/hello'), String)).toFuture()
        awaitLookup(discoveryClient)

        when:
        discoveryClient.futures[0].complete([])
        response.join()

        then:
        def e = thrown(CompletionException)
        e.cause instanceof NoAvailableServiceException
    }

    /**
     * Fails the publisher methods, so that only the stages can serve.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerSelectAsyncSpec')
    static class PendingDiscoveryClient implements DiscoveryClient, AsyncDiscoveryClient {
        final List<String> requested = new CopyOnWriteArrayList<>()
        final List<CompletableFuture<List<ServiceInstance>>> futures = new CopyOnWriteArrayList<>()

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            throw new UnsupportedOperationException('publisher')
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            throw new UnsupportedOperationException('publisher')
        }

        @Override
        CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
            // a new stage for each call, which the framework may cancel
            CompletableFuture<List<ServiceInstance>> future = CompletionStagePublishers.future()
            requested << serviceId
            futures << future
            return future
        }

        @Override
        CompletionStage<List<String>> getServiceIdsAsync() {
            CompletableFuture.completedFuture(['pending'])
        }

        @Override
        String getDescription() {
            'pending'
        }

        @Override
        void close() {
            // The fixture has no resources; the application context owns its client.
        }
    }

    @Controller('/jdk-select-async')
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerSelectAsyncSpec')
    static class HelloController {
        @Get('/hello')
        String hello() {
            'hello'
        }
    }
}
