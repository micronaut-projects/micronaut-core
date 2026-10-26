package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Property sources added to and removed from a running environment, also while a start or a refresh
 * reads the property sources. An add is processed on top of the published properties and then
 * registered and published in one step. While a refresh is in progress, an add made before the
 * refresh has taken the snapshot of the added property sources is only registered, for the refresh
 * to process in its order; one made after is published at once and kept as a late add of the
 * refresh, which keeps it on top.
 * The threads are parked at fixed points, and every wait is bounded.
 */
@Timeout(60)
class AddPropertySourceDuringReadSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13982')
    void "a property source added while a #refresh processes the property sources is visible at once, and the refresh publishes it in its order"() {
        given: "a property source of a higher order with the same key, and a gate that parks the refresh after the others"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a', 'shared.key': 'data'], 100))
        env.addPropertySource(gate)
        if (refresh == 'refresh') {
            env.start()
        }

        when: "it is added while the refresh is parked"
        Thread refresher = parkIn(gate) { refresh == 'refresh' ? env.refresh() : env.start() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late', 'shared.key': 'late'], -100))
        }
        String duringRefresh = env.getProperty('late.key', String).orElse(null)
        String sharedDuringRefresh = env.getProperty('shared.key', String).orElse(null)
        release(gate, refresher)

        then: "it was visible once added, on top, as an added property source is"
        duringRefresh == 'late'
        sharedDuringRefresh == 'late'

        and: "the refresh publishes it in its order with what it has read, and it stays registered"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('shared.key', String).get() == 'data'
        env.getProperty('app.value', String).get() == 'a'
        env.propertySources*.name.contains('late')

        when: "the environment is refreshed again"
        env.refresh()

        then: "it is read in its order"
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('shared.key', String).get() == 'data'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        refresh << ['start', 'refresh']
    }

    void "a property source added before a refresh takes the registered property sources is only registered, and the refresh processes it in its order"() {
        given: "a configuration that, once armed, parks the refresh while it loads the default property sources"
        AtomicBoolean armed = new AtomicBoolean()
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() {
                if (armed.compareAndSet(true, false)) {
                    parked.countDown()
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                }
                return ['NO_SUCH_VARIABLE']
            }
        })
        env.addPropertySource(PropertySource.of('data', ['shared.key': 'data'], 100))
        env.start()

        when: "it is added while the refresh loads the default property sources"
        armed.set(true)
        Thread refresher = daemon('refresher') { env.refresh() }
        assert parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        changeWhileParked {
            env.addPropertySource(PropertySource.of('early', ['early.key': 'early', 'shared.key': 'early'], -100))
        }
        boolean visibleBeforeSnapshot = env.containsProperty('early.key')
        release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh has processed it, in its order"
        !refresher.alive
        !visibleBeforeSnapshot
        env.getProperty('early.key', String).get() == 'early'
        env.getProperty('shared.key', String).get() == 'data'

        cleanup:
        release.countDown()
        env.stop()
    }

    void "a property source added and removed again while a refresh processes the property sources is gone once the refresh publishes"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        PropertySource late = PropertySource.of('late', ['late.key': 'late'])

        when:
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            env.addPropertySource(late)
            env.removePropertySource(late)
        }
        release(gate, refresher)

        then:
        !env.getProperty('late.key', String).present
        !env.propertySources*.name.contains('late')
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that fails to be processed, added while a refresh processes the property sources, is logged and stays registered, and the refresh publishes"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> data = ['app.value': 'a']
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()
        Throwable addFailure = null

        when:
        data.put('app.value', 'b')
        Thread refresher = parkIn(gate) { env.refresh() }
        changeWhileParked {
            try {
                env.addPropertySource(new FailsWhenIterated('failing'))
            } catch (Throwable t) {
                addFailure = t
            }
        }
        release(gate, refresher)

        then: "the add does not fail, and the refresh publishes what it read"
        addFailure == null
        env.propertySources*.name.contains('failing')
        env.getProperty('app.value', String).get() == 'b'

        when: "the environment is refreshed again"
        env.refresh()

        then: "the next refresh reads it, and fails on it, as before"
        thrown(IllegalStateException)

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that fails part way, added outside a refresh, publishes none of its properties and stays registered"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.start()

        when:
        env.addPropertySource(new FailsPartWay())

        then:
        thrown(IllegalStateException)
        !env.containsProperty('partial.first')
        env.propertySources*.name.contains('partial')
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        env.stop()
    }

    void "adding the registered property sources again in their order keeps their precedence, as a configuration watch does"() {
        given: "a property source of a higher order than the one that changes, which sets the same key"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        CountsIterations counting = new CountsIterations()
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'remote-v1', 'app.remote': 'v1'], 10))
        env.addPropertySource(PropertySource.of('override', ['app.value': 'override'], 20))
        env.addPropertySource(counting)
        env.start()

        when: "the changed property source replaces its name, and every registered one is added again by order"
        List<PropertySource> updated = env.propertySources.collect {
            it.name == 'remote' ? PropertySource.of('remote', ['app.value': 'remote-v2', 'app.remote': 'v2'], 10) : it
        }
        updated.sort { it.order }.each { env.addPropertySource(it) }

        then: "the registered ones are processed again on top, in their order"
        counting.iterations.get() == 2
        env.getProperty('app.remote', String).get() == 'v2'
        env.getProperty('app.value', String).get() == 'override'

        cleanup:
        env.stop()
    }

    void "a property source added while a refresh publishes the properties is processed again on top of them, as if it had been processed on top of them"() {
        given: "a property source with a list, and one that overrides an element of it and parks the add once it has"
        Map<String, Object> data = ['app.list': ['a', 'b'], 'app.other': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        ParksOnMarker overriding = new ParksOnMarker(['app.list[0]': 'q', 'app.marker': 'added'])
        Thread adder = daemon('adder') { env.addPropertySource(overriding) }

        when: "a refresh changes the list and publishes while the add is parked"
        assert overriding.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('app.list', ['c', 'd', 'e'])
        data.put('app.other', 'c')
        Thread refresher = daemon('refresher') { env.refresh() }
        refresher.join(WAIT_SECONDS * 1000L)
        boolean refreshedWhileParked = !refresher.alive
        overriding.release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh did not wait for the add"
        refreshedWhileParked
        !adder.alive

        and: "the add is on top of what the refresh read"
        env.getRequiredProperty('app.list', List) == ['q', 'd', 'e']
        env.getRequiredProperty('app.other', String) == 'c'
        env.getRequiredProperty('app.marker', String) == 'added'

        cleanup:
        overriding.release.countDown()
        env.stop()
    }

    void "a property source that adds another while it is processed by a #change sees the one it added"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        AddsWhenProcessed source = new AddsWhenProcessed(env)

        when:
        if (change == 'start') {
            env.addPropertySource(source)
            env.start()
        } else if (change == 'refresh') {
            env.addPropertySource(source)
            env.start()
            source.reset()
            env.refresh()
        } else {
            env.start()
            env.addPropertySource(source)
        }

        then:
        source.observed == 'nested'
        env.getProperty('nested.value', String).get() == 'nested'

        cleanup:
        env.stop()

        where:
        change << ['start', 'refresh', 'add']
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13913')
    void "a random value that a property source reads while the environment starts stays the value of later lookups when a property source is added meanwhile"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('random', ['app.id': '${random.uuid}'], 1))
        env.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'derived' }

            @Override
            int getOrder() { 2 }

            @Override
            Object get(String key) {
                key == 'derived.id' ? env.getProperty('app.id', String).orElse(null) : null
            }

            @Override
            Iterator<String> iterator() { ['derived.id'].iterator() }
        })
        env.addPropertySource(gate)

        when: "a property source is added while the start is parked after the others"
        Thread starter = parkIn(gate) { env.start() }
        changeWhileParked {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }
        String duringStart = env.getProperty('app.id', String).orElse(null)
        release(gate, starter)

        then:
        env.getRequiredProperty('derived.id', String) == env.getRequiredProperty('app.id', String)
        duringStart == env.getRequiredProperty('app.id', String)
        env.getRequiredProperty('late.key', String) == 'late'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * Runs the refresh on another thread, once the gate is armed, and returns that thread once the
     * refresh is parked in the gate.
     */
    private static Thread parkIn(Gate gate, Closure<?> refresh) {
        gate.arm()
        Thread refresher = daemon('refresher', refresh)
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return refresher
    }

    /**
     * Runs the change on another thread, which must finish while the refresh is parked.
     */
    private static void changeWhileParked(Closure<?> change) {
        Thread changer = daemon('changer', change)
        changer.join(WAIT_SECONDS * 1000L)
        assert !changer.alive
    }

    private static void release(Gate gate, Thread refresher) {
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        assert !refresher.alive
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * A property source without properties, processed after the others, that once armed parks the
     * thread that iterates it.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(AddPropertySourceDuringReadSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source that fails to be iterated.
     */
    static class FailsWhenIterated implements PropertySource {
        private final String name

        FailsWhenIterated(String name) {
            this.name = name
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            throw new IllegalStateException('failed to iterate the property source')
        }
    }

    /**
     * A property source whose second value fails to be read.
     */
    static class FailsPartWay implements PropertySource {
        @Override
        String getName() { 'partial' }

        @Override
        Object get(String key) {
            if (key == 'partial.second') {
                throw new IllegalStateException('failed to read the value')
            }
            return 'first'
        }

        @Override
        Iterator<String> iterator() { ['partial.first', 'partial.second'].iterator() }
    }

    /**
     * A property source that counts how often it is iterated.
     */
    static class CountsIterations implements PropertySource {
        final AtomicInteger iterations = new AtomicInteger()

        @Override
        String getName() { 'counted' }

        @Override
        Object get(String key) { key == 'counted.key' ? 'counted' : null }

        @Override
        Iterator<String> iterator() {
            iterations.incrementAndGet()
            return ['counted.key'].iterator()
        }
    }

    /**
     * A property source with the given values that parks the thread that reads its value of
     * {@code app.marker}, which it gives last.
     */
    static class ParksOnMarker implements PropertySource {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final Map<String, Object> values

        ParksOnMarker(Map<String, Object> values) {
            this.values = values
        }

        @Override
        String getName() { 'overriding' }

        @Override
        Object get(String key) {
            if (key == 'app.marker') {
                reached.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(AddPropertySourceDuringReadSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the add was never released')
                }
            }
            return values.get(key)
        }

        @Override
        Iterator<String> iterator() { values.keySet().iterator() }
    }

    /**
     * A property source that, when it is iterated, adds another property source and looks up its
     * property.
     */
    static class AddsWhenProcessed implements PropertySource {
        volatile String observed
        private final Environment env
        private final AtomicBoolean once = new AtomicBoolean(true)

        AddsWhenProcessed(Environment env) {
            this.env = env
        }

        void reset() {
            once.set(true)
            observed = null
            env.removePropertySource(PropertySource.of('nested', [:]))
        }

        @Override
        String getName() { 'nested-parent' }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (once.compareAndSet(true, false)) {
                env.addPropertySource(PropertySource.of('nested', ['nested.value': 'nested']))
                observed = env.getProperty('nested.value', String).orElse('missing')
            }
            return Collections.emptyIterator()
        }
    }
}
