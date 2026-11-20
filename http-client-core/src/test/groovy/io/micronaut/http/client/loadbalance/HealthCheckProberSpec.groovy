package io.micronaut.http.client.loadbalance

import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.StaticServiceInstanceList
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
                             Closure<?> probe = { URI uri -> Mono.just(health.getOrDefault(uri, true)) },
                             Closure<?> instances = { -> Mono.fromCallable { list.instances } }) {
        new HealthCheckProber('svc',
            new HealthCheckProber.Settings(Duration.ofSeconds(30), 0.1d, timeout, concurrency, healthy, unhealthy),
            { -> instances.call() },
            { ServiceInstance instance -> probe.call(instance.URI) },
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

    void "with the none policy no instance is used when all are unhealthy"() {
        given:
        def prober = prober()
        loadBalancer.enableHealthCheck().setNoneWhenAllUnhealthy(true)

        when:
        health[A] = false
        health[B] = false
        prober.probeOnce().join()
        Mono.from(loadBalancer.select()).block()

        then:
        thrown(io.micronaut.discovery.exceptions.NoAvailableServiceException)

        when: 'the policy carries over when the outlier detection is replaced'
        loadBalancer.setOutlierDetection(new OutlierDetectionConfiguration(enabled: true))
        Mono.from(loadBalancer.select()).block()

        then:
        thrown(io.micronaut.discovery.exceptions.NoAvailableServiceException)

        when:
        health[B] = true
        prober.probeOnce().join()

        then:
        selected() == [B] as Set
    }

    void "the default settings follow the interval"() {
        expect:
        HealthCheckProber.Settings.of(Duration.ofSeconds(30)).timeout() == Duration.ofSeconds(10)
        HealthCheckProber.Settings.of(Duration.ofMillis(100)).timeout() == Duration.ofMillis(100)
        HealthCheckProber.Settings.of(Duration.ofSeconds(30)).healthyThreshold() == 1
        HealthCheckProber.Settings.of(Duration.ofSeconds(30)).unhealthyThreshold() == 1
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

    void "a lookup that throws or never completes keeps the last results and ends the round"() {
        given:
        boolean failLookup = false
        boolean stallLookup = false
        def prober = prober(1, 1, Duration.ofMillis(100), 8, { URI uri -> Mono.just(health.getOrDefault(uri, true)) }, { ->
            if (failLookup) {
                throw new IllegalStateException('discovery down')
            }
            stallLookup ? Mono.never() : Mono.fromCallable { list.instances }
        })
        health[B] = false
        prober.probeOnce().join()

        when:
        failLookup = true
        def thrown = prober.probeOnce()

        then:
        thrown.get(5, TimeUnit.SECONDS) == null
        state(B).unhealthy()

        when:
        failLookup = false
        stallLookup = true

        then:
        prober.probeOnce().get(5, TimeUnit.SECONDS) == null
        state(B).unhealthy()
    }

    void "a probe that throws fails only its own instance"() {
        given:
        uris.add(C)
        def prober = prober(1, 1, Duration.ofSeconds(5), 1, { URI uri ->
            if (uri == A) {
                throw new IllegalStateException('cannot build the request')
            }
            Mono.just(false)
        })

        when:
        prober.probeOnce().join()

        then:
        state(A).unhealthy()
        state(B).unhealthy()
        state(C).unhealthy()
    }

    void "replacing the outlier detection keeps the health check attached"() {
        given:
        def prober = prober()
        health[B] = false
        prober.probeOnce().join()

        when:
        loadBalancer.setOutlierDetection(new OutlierDetectionConfiguration(enabled: true))

        then: 'the results carried over'
        state(B).unhealthy()
        selected() == [A] as Set

        when: 'the prober keeps reporting to what the selection reads'
        loadBalancer.setOutlierDetection(null)
        health[B] = true
        prober.probeOnce().join()

        then:
        !state(B).unhealthy()
        selected() == [A, B] as Set
    }

    void "closing cancels the probes of the running round"() {
        given:
        def started = new AtomicInteger()
        uris.add(C)
        def prober = prober(1, 1, Duration.ofSeconds(5), 1, { URI uri ->
            started.incrementAndGet()
            Mono.just(true).delayElement(Duration.ofMillis(200))
        })

        when:
        def round = prober.probeOnce()
        Thread.sleep(50)
        prober.close()
        round.get(5, TimeUnit.SECONDS)
        Thread.sleep(500)

        then:
        started.get() == 1
        loadBalancer.outlierEjectionStates.every { !it.unhealthy() }
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
        new HealthCheckProber.Settings(Duration.ofSeconds(1), jitter, Duration.ofSeconds(1), 1, 1, 1)

        then:
        thrown(IllegalArgumentException)

        where:
        jitter << [1d, -0.1d, Double.NaN]
    }
}
