package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpVersion
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.HttpVersionSelection
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.LoadBalancerResolver
import io.micronaut.http.client.RawHttpClientRegistry
import io.micronaut.http.client.loadbalance.AbstractRoundRobinLoadBalancer
import io.micronaut.http.client.loadbalance.LoadBalancerStrategy
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import org.jspecify.annotations.Nullable
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every selection of an instance by the load balancer is reported exactly once by the JDK
 * clients, whether its exchange completes, fails or is cancelled.
 */
class JdkLoadBalancerSelectionReleaseSpec extends Specification {

    static final String SPEC = 'JdkLoadBalancerSelectionReleaseSpec'

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': SPEC])

    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run([
            'spec.name': SPEC,
            'micronaut.http.services.p2c.urls': ["http://localhost:${server.port}".toString(), "http://127.0.0.1:${server.port}".toString()],
            'micronaut.http.services.p2c.load-balancer-strategy': 'counting-jdk',
    ])

    CountingStrategy strategy() {
        LoadBalancer loadBalancer = ctx.getBean(LoadBalancerResolver).resolve('p2c').orElseThrow()
        return (CountingStrategy) ((AbstractRoundRobinLoadBalancer) loadBalancer).strategy
    }

    void allReleased(CountingStrategy strategy) {
        new PollingConditions(timeout: 5).eventually {
            assert strategy.selected.get() == strategy.reported.get()
        }
    }

    def 'a cancelled exchange releases its instance'() {
        given:
        HttpClient client = ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, 'p2c', null)
        CountingStrategy strategy = strategy()

        expect:
        client.toBlocking().retrieve('/jdk-lb-release/fast') == 'fast'

        when:
        Mono.from(client.retrieve(HttpRequest.GET('/jdk-lb-release/slow'), String)).timeout(Duration.ofMillis(300)).block()

        then:
        thrown(Exception)
        allReleased(strategy)
        strategy.outcomes[LoadBalancer.Outcome.CANCELLED].get() == 1
        strategy.outcomes[LoadBalancer.Outcome.SUCCESS].get() == 1
    }

    def 'a cancelled raw exchange releases its instance'() {
        given:
        def raw = ctx.getBean(RawHttpClientRegistry).getRawClient(HttpVersionSelection.forLegacyVersion(HttpVersion.HTTP_1_1), 'p2c', null)
        CountingStrategy strategy = strategy()

        when:
        Mono.from(raw.exchange(HttpRequest.GET('/jdk-lb-release/slow'), null, null)).timeout(Duration.ofMillis(300)).block()

        then:
        thrown(Exception)
        allReleased(strategy)
        strategy.outcomes[LoadBalancer.Outcome.CANCELLED].get() == 1

        cleanup:
        raw.close()
    }

    def 'the p2c strategy keeps selecting both instances after a cancelled exchange'() {
        given:
        HttpClient client = ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, 'p2c', null)
        CountingStrategy strategy = strategy()

        when:
        Mono.from(client.retrieve(HttpRequest.GET('/jdk-lb-release/slow'), String)).timeout(Duration.ofMillis(300)).block()

        then:
        thrown(Exception)
        allReleased(strategy)

        when:
        strategy.hosts.clear()
        50.times { client.toBlocking().retrieve('/jdk-lb-release/fast') }

        then:
        strategy.hosts.size() == 2
    }

    @Prototype
    @Named('counting-jdk')
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerSelectionReleaseSpec')
    static class CountingStrategy implements LoadBalancerStrategy {
        final LoadBalancerStrategy p2c = LoadBalancerStrategy.of(LoadBalancerStrategy.POWER_OF_TWO_CHOICES)
        final AtomicInteger selected = new AtomicInteger()
        final AtomicInteger reported = new AtomicInteger()
        final Map<LoadBalancer.Outcome, AtomicInteger> outcomes = new ConcurrentHashMap<>(LoadBalancer.Outcome.values().collectEntries { [(it): new AtomicInteger()] })
        final Map<String, AtomicInteger> hosts = new ConcurrentHashMap<>()

        @Override
        ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
            ServiceInstance instance = p2c.select(available, discriminator)
            selected.incrementAndGet()
            hosts.computeIfAbsent(instance.URI.host, h -> new AtomicInteger()).incrementAndGet()
            return instance
        }

        @Override
        void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
            p2c.report(instance, outcome)
            outcomes[outcome].incrementAndGet()
            reported.incrementAndGet()
        }
    }

    @Controller('/jdk-lb-release')
    @Requires(property = 'spec.name', value = 'JdkLoadBalancerSelectionReleaseSpec')
    static class SlowController {
        @Get('/fast')
        String fast() {
            return 'fast'
        }

        @Get('/slow')
        Mono<String> slow() {
            return Mono.just('slow').delayElement(Duration.ofSeconds(2))
        }
    }
}
