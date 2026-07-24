package io.micronaut.http.server.netty.context

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.NonNull
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.http.scope.RequestScope
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DiscoveryClientRequestScopePropagationSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer backendServer = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'DiscoveryClientRequestScopePropagationSpec.backend'
    ])

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'DiscoveryClientRequestScopePropagationSpec',
        'micronaut.caches.discovery-client.enabled': false,
        'reactor.enableAutomaticContextPropagation': true,
        'backend.url': "http://localhost:$backendServer.port"
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Shared
    BlockingHttpClient client = httpClient.toBlocking()

    void "request scope is available inside discovery client with automatic context propagation enabled"() {
        expect:
        client.retrieve('/test') == 'resolved'
    }

    @Requires(property = 'spec.name', value = 'DiscoveryClientRequestScopePropagationSpec')
    @Controller
    static class TestController {
        @Inject
        TestApiClient testApiClient

        @Get('/test')
        @ExecuteOn(TaskExecutors.BLOCKING)
        String test() {
            testApiClient.call()
            return 'resolved'
        }
    }

    @Requires(property = 'spec.name', value = 'DiscoveryClientRequestScopePropagationSpec')
    @Client('test-service')
    static interface TestApiClient {
        @Get('/backend')
        void call()
    }

    @Requires(property = 'spec.name', value = 'DiscoveryClientRequestScopePropagationSpec')
    @Singleton
    static class TestDiscoveryClient implements DiscoveryClient {
        @Inject
        RequestScopedBean requestScopedBean

        @Value('${backend.url}')
        String backendUrl

        @Override
        void close() {
        }

        @Override
        @NonNull
        String getDescription() {
            'test'
        }

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            requestScopedBean.value()
            return Mono.just([ServiceInstance.of(serviceId, new URL(backendUrl))])
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            return Mono.just(['test-service'])
        }
    }

    @Requires(property = 'spec.name', value = 'DiscoveryClientRequestScopePropagationSpec')
    @RequestScope
    static class RequestScopedBean {
        String value() {
            'ok'
        }
    }

    @Requires(property = 'spec.name', value = 'DiscoveryClientRequestScopePropagationSpec.backend')
    @Controller
    static class BackendController {
        @Get('/backend')
        void backend() {
        }
    }
}
