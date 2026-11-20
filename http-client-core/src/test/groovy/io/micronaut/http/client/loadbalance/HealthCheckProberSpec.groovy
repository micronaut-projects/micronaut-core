package io.micronaut.http.client.loadbalance

import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.StaticServiceInstanceList
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HealthCheckProberSpec extends Specification {

    static final URI A = URI.create('http://a:8080')
    static final URI B = URI.create('http://b:8080')
    static final URI C = URI.create('http://c:8080')

    Collection<URI> uris = new ArrayList<>([A, B])
    StaticServiceInstanceList list = new StaticServiceInstanceList('svc', uris)
    ServiceInstanceListRoundRobinLoadBalancer loadBalancer = new ServiceInstanceListRoundRobinLoadBalancer(list)
    Map<URI, Boolean> health = new ConcurrentHashMap<>()

    HealthCheckProber prober(int healthy = 1, int unhealthy = 1, Duration timeout = Duration.ofSeconds(5), int concurrency = 8,
                             Closure<?> probe = { URI uri -> Mono.just(health.getOrDefault(uri, true)) }) {
        new HealthCheckProber('svc',
            new HealthCheckProber.Settings(Duration.ofSeconds(30), 0.1d, timeout, concurrency, healthy, unhealthy),
            { -> Mono.fromCallable { list.instances } },
            { URI uri -> probe.call(uri) },
            loadBalancer.enableHealthCheck(),
            null)
    }

    private Set<URI> selected(int times = 10) {
        (1..times).collect { Mono.from(loadBalancer.select()).block().URI } as Set
    }

    private OutlierEjectionState state(URI uri) {
        loadBalancer.outlierEjectionStates.find { it.uri() == uri }
    }

    void "an unhealthy instance is skipped and returns once it recovers"() {
        given:
        def prober = prober()

        when:
        health[B] = false
        prober.probeOnce().join()

        then:
        selected() == [A] as Set
        state(B).unhealthy()
        state(B).excluded()
        !state(B).ejected()
        !state(A).unhealthy()

        when:
        health[B] = true
        prober.probeOnce().join()

        then:
        selected() == [A, B] as Set
        !state(B).unhealthy()
    }

    void "the thresholds need consecutive results"() {
        given:
        def prober = prober(2, 3)

        when:
        health[B] = false
        prober.probeOnce().join()
        prober.probeOnce().join()

        then:
        !state(B).unhealthy()

        when:
        health[B] = true
        prober.probeOnce().join()
        health[B] = false
        prober.probeOnce().join()
        prober.probeOnce().join()

        then: 'the success restarted the count'
        !state(B).unhealthy()

        when:
        prober.probeOnce().join()

        then:
        state(B).unhealthy()

        when:
        health[B] = true
        prober.probeOnce().join()

        then:
        state(B).unhealthy()

        when:
        prober.probeOnce().join()

        then:
        !state(B).unhealthy()
    }

    void "the probes follow the instance list"() {
        given:
        def probed = new ConcurrentHashMap<URI, AtomicInteger>()
        def prober = prober(1, 1, Duration.ofSeconds(5), 8, { URI uri ->
            probed.computeIfAbsent(uri, { new AtomicInteger() }).incrementAndGet()
            Mono.just(health.getOrDefault(uri, true))
        })
        health[B] = false
        prober.probeOnce().join()

        expect:
        state(B).unhealthy()

        when: 'B leaves, C joins'
        uris.remove(B)
        uris.add(C)
        prober.probeOnce().join()

        then:
        probed[C].get() == 1
        probed[B].get() == 1
        state(B) == null
        selected() == [A, C] as Set

        when: 'B comes back: its old result was forgotten'
        health[B] = true
        uris.add(B)
        selected(1)

        then:
        !state(B).unhealthy()
    }

    void "when every instance is unhealthy all of them are used"() {
        given:
        def prober = prober()

        when:
        health[A] = false
        health[B] = false
        prober.probeOnce().join()

        then:
        state(A).unhealthy()
        state(B).unhealthy()
        selected() == [A, B] as Set
    }

    void "a probe that fails, errors or times out marks the instance unhealthy"() {
        given:
        def prober = prober(1, 1, Duration.ofMillis(50), 8, { URI uri ->
            uri == A ? Mono.error(new IOException('refused')) : Mono.never()
        })
        uris.add(C)
        health[C] = true

        when:
        prober.probeOnce().join()

        then:
        state(A).unhealthy()
        state(B).unhealthy()
    }

    void "the probes of a round are bounded"() {
        given:
        def inFlight = new AtomicInteger()
        def max = new AtomicInteger()
        uris.add(C)
        def prober = prober(1, 1, Duration.ofSeconds(5), 2, { URI uri ->
            Mono.fromCallable {
                max.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
                true
            }.delayElement(Duration.ofMillis(50)).map { inFlight.decrementAndGet(); it }
        })

        when:
        prober.probeOnce().join()

        then:
        max.get() <= 2
        max.get() >= 1
    }

    void "probe results and outlier ejection share one view"() {
        given:
        def outlier = new OutlierDetectionConfiguration(enabled: true, consecutiveFailures: 1, maxEjectionPercent: 100)
        loadBalancer.setOutlierDetection(outlier)
        def prober = prober()

        when:
        health[B] = false
        prober.probeOnce().join()
        loadBalancer.report(ServiceInstance.of('svc', A), io.micronaut.http.client.LoadBalancer.Outcome.CONNECT_FAILURE)

        then:
        state(A).ejected()
        state(B).unhealthy()
        selected() == [A, B] as Set // panic: nothing left, all of them are used
    }

    void "the jitter spreads the interval"() {
        given:
        def prober = prober()

        expect:
        (1..50).every {
            def d = prober.nextDelay()
            d >= Duration.ofSeconds(27) && d <= Duration.ofSeconds(33)
        }
    }

    void "invalid settings are refused"() {
        when:
        new HealthCheckProber.Settings(Duration.ofSeconds(1), 1d, Duration.ofSeconds(1), 1, 1, 1)

        then:
        thrown(IllegalArgumentException)
    }
}
