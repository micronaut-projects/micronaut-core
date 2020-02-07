package io.micronaut.discovery.composite

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.MockConsulServer
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton

class CompositeDiscoverySpec extends Specification {

    @AutoCleanup
    @Shared
    EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer, [
            (MockConsulServer.ENABLED):true
    ])

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['micronaut.application.name'              : 'foo',
             "micronaut.caches.discoveryClient.enabled": false,
             'consul.client.host'                     : 'localhost',
             'consul.client.port'                     : consulServer.getPort(),
             'spec.name': 'CompositeDiscoverySpec'])

    void "test multiple service discovery clients"() {
        given:
        DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
        PollingConditions conditions = new PollingConditions(timeout: 3)

        expect:
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances('foo')).blockingFirst()
            instances.size() == 2
            instances.stream().anyMatch({ instance -> instance.host == "foo" && instance.port == 8443 })
            instances.stream().anyMatch({ instance -> instance.host == "localhost" && instance.port == embeddedServer.getPort() })
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "CompositeDiscoverySpec")
    static class MockDiscoveryClient implements DiscoveryClient {

        @Override
        Publisher<List<ServiceInstance>> getInstances(String serviceId) {
            return Publishers.just([new ServiceInstance() {
                @Override
                String getId() {
                    return "foo"
                }

                @Override
                URI getURI() {
                    return new URI("http://foo:8443")
                }
            }])
        }

        @Override
        Publisher<List<String>> getServiceIds() {
            return ["foo"]
        }

        @Override
        String getDescription() {
            return "Mock"
        }

        @Override
        void close() throws IOException {

        }
    }
}
