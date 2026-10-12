package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.order.Ordered
import io.micronaut.core.util.StringUtils
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions
import spock.util.environment.RestoreSystemProperties

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

/**
 * Lookups made while, or around, {@code Environment#refresh()} rebuilding the property catalogs.
 * The threads are sequenced with latches: one is parked inside a property source, a converter or a
 * hook of the resolver while the spec thread observes, and only then is the latch released. The
 * two tests about the lock additionally wait until the other thread is blocked on it.
 */
class RefreshConcurrentLookupSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/12941")
    void "a lookup made while a refresh rebuilds the catalogs sees the properties as they were before it"() {
        given:
        Map<String, Object> data = ['myApp.fooBar': 'old', 'plain.key': 'old']
        Gate gate = new Gate(Ordered.HIGHEST_PRECEDENCE)
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.addPropertySource(gate)
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()

        when: "the values change and the refresh is parked before it has re-read any property source"
        data.put('myApp.fooBar', 'new')
        data.put('plain.key', 'new')
        Map<String, Object> mid = refreshThroughGate(env, gate) {
            [
                    generatedSpelling: env.getProperty('my-app.foo-bar', String).orElse(null),
                    rawSpelling      : env.getProperty('plain.key', String).orElse(null),
                    contains         : env.containsProperty('my-app.foo-bar'),
                    containsAny      : env.containsProperties('my-app'),
                    properties       : env.getProperties('my-app')
            ]
        }

        then: "another thread still resolves them, in a spelling that only the generated catalog holds too"
        mid.generatedSpelling == 'old'
        mid.rawSpelling == 'old'
        mid.contains
        mid.containsAny
        mid.properties == ['foo-bar': 'old']

        and: "what that thread resolved is not cached past the refresh"
        env.getProperty('my-app.foo-bar', String).get() == 'new'
        env.getProperty('plain.key', String).get() == 'new'
        env.containsProperty('my-app.foo-bar')

        cleanup:
        env.stop()
    }

    void "a lookup made while a refresh rebuilds the catalogs does not see a value that a later property source overrides"() {
        given: "the same key in a low and in a high priority source, and a gate between the two"
        Gate gate = new Gate(15)
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.addPropertySource(PropertySource.of('low', ['shadowed.key': 'low'], 10))
        env.addPropertySource(gate)
        env.addPropertySource(PropertySource.of('high', ['shadowed.key': 'high'], 20))
        env.start()

        when: "the refresh is parked after it has processed the low priority source only"
        String mid = refreshThroughGate(env, gate) {
            env.getProperty('shadowed.key', String).orElse(null)
        }

        then:
        mid == 'high'
        env.getProperty('shadowed.key', String).get() == 'high'

        cleanup:
        env.stop()
    }

    @RestoreSystemProperties
    void "a bootstrap enabled context resolves the previous values while its refresh re-reads the bootstrap configuration"() {
        given:
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, 'ms')
        Path dir = Files.createTempDirectory('refresh-bootstrap-')
        File file = dir.resolve('bootstrap-ms.yml').toFile()
        file.text = 'myApp.fooBar: one\nkafka.bootstrap.servers: broker-one:9092\n'
        Gate gate = new Gate(Ordered.HIGHEST_PRECEDENCE)
        ApplicationContext ctx = ApplicationContext.builder()
                .overrideConfigLocations('file:' + dir)
                .propertySources(gate)
                .start()
        Environment env = ctx.environment

        when: "the refresh is parked while it starts the new bootstrap environment"
        file.text = 'myApp.fooBar: two\nkafka.bootstrap.servers: broker-two:9092\n'
        Map<String, Object> mid = refreshThroughGate(env, gate) {
            [
                    value   : env.getProperty('my-app.foo-bar', String).orElse(null),
                    // how a configuration is read by prefix, by the default configuration of micronaut-kafka for one,
                    // which falls back to a default broker when the map lacks the key
                    byPrefix: env.containsProperties('kafka') ? env.getProperties('kafka') : [:]
            ]
        }

        then:
        mid.value == 'one'
        mid.byPrefix['bootstrap.servers'] == 'broker-one:9092'
        env.getProperty('my-app.foo-bar', String).get() == 'two'
        env.getProperties('kafka')['bootstrap.servers'] == 'broker-two:9092'

        cleanup:
        ctx?.close()
        file?.delete()
        dir?.toFile()?.delete()
    }

    void "a miss the refreshing thread caches while it rebuilds the catalogs is not published"() {
        given: "a property source, read between two others, that asks for a key the later one provides"
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        PropertySource probing = new PropertySource() {
            @Override
            String getName() { 'probing' }

            @Override
            Object get(String key) { null }

            @Override
            int getOrder() { 15 }

            @Override
            Iterator<String> iterator() {
                env.containsProperty('late.key')
                env.getProperty('late.key', String)
                return Collections.emptyIterator()
            }
        }
        env.addPropertySource(PropertySource.of('early', ['lead.key': 'value'], 10))
        env.addPropertySource(probing)
        env.addPropertySource(PropertySource.of('late', ['late.key': 'value'], 20))

        when:
        env.start()

        then:
        env.containsProperty('late.key')
        env.getProperty('late.key', String).get() == 'value'

        when:
        env.refresh()

        then:
        env.containsProperty('late.key')
        env.getProperty('late.key', String).get() == 'value'

        cleanup:
        env.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/12190")
    void "a lookup that started before a refresh does not cache the old value over the refreshed one"() {
        given: "a value whose conversion can be parked, that is between reading the catalog and caching the result"
        AtomicBoolean armed = new AtomicBoolean()
        CountDownLatch converting = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        Map<String, Object> data = ['slow.value': new SlowValue(number: 1)]
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.addPropertySource(new MapPropertySource('data', data))
        env.addConverter(SlowValue, Integer, { SlowValue slow ->
            if (armed.compareAndSet(true, false)) {
                converting.countDown()
                release.await(WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return slow.number
        } as Function<SlowValue, Integer>)
        env.start()
        ExecutorService executor = Executors.newSingleThreadExecutor()

        when: "a refresh runs to completion while a reader is parked converting the old value"
        armed.set(true)
        Future<Integer> reader = executor.submit({ env.getProperty('slow.value', Integer).get() } as Callable<Integer>)
        assert converting.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('slow.value', new SlowValue(number: 2))
        env.refresh()
        release.countDown()

        then: "the reader gets the value it started with, and every lookup after it the refreshed one"
        reader.get(WAIT_SECONDS, TimeUnit.SECONDS) == 1
        env.getProperty('slow.value', Integer).get() == 2

        cleanup:
        release.countDown()
        executor.shutdownNow()
        env.stop()
    }

    void "a property removed from its source does not resolve after a refresh"() {
        given:
        Map<String, Object> data = ['removed.key': 'gone', 'removedProp.someKey': 'gone', 'retained.key': 'kept']
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()

        when:
        data.remove('removed.key')
        data.remove('removedProp.someKey')
        Map<String, Object> diff = env.refreshAndDiff()

        then:
        diff == ['removed.key': 'gone', 'removed-prop.some-key': 'gone']
        !env.getProperty('removed.key', String).isPresent()
        !env.getProperty('removedProp.someKey', String).isPresent()
        !env.containsProperty('removed.key')
        !env.containsProperties('removed')
        env.getProperties('removed', StringConvention.RAW).isEmpty()
        env.getPropertyEntries('removed').isEmpty()
        env.getProperty('retained.key', String).get() == 'kept'

        cleanup:
        env.stop()
    }

    void "a refresh that fails keeps the properties as they were"() {
        given: "a property source, read before the others, that can be told to fail"
        AtomicBoolean fail = new AtomicBoolean()
        PropertySource failing = new PropertySource() {
            @Override
            String getName() { 'failing' }

            @Override
            Object get(String key) { null }

            @Override
            int getOrder() { Ordered.HIGHEST_PRECEDENCE }

            @Override
            Iterator<String> iterator() {
                if (fail.get()) {
                    throw new IllegalStateException('failed while being re-read')
                }
                return Collections.emptyIterator()
            }
        }
        Map<String, Object> data = ['myApp.fooBar': 'old']
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.addPropertySource(failing)
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()

        when:
        fail.set(true)
        data.put('myApp.fooBar', 'new')
        env.refresh()

        then:
        thrown(IllegalStateException)
        env.getProperty('my-app.foo-bar', String).get() == 'old'
        env.getProperties('my-app') == ['foo-bar': 'old']

        when: "a property source is added before the next refresh"
        env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))

        then: "it is processed: the failed refresh has not left the environment marked as reading"
        env.getProperty('late.key', String).get() == 'late'

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then:
        env.getProperty('my-app.foo-bar', String).get() == 'new'

        cleanup:
        env.stop()
    }

    @RestoreSystemProperties
    void "a refresh after a refresh that failed to read a configuration file reads the file as it is then"() {
        given:
        File config = File.createTempFile('refresh-failed', '.properties')
        config.write('app.main=one')
        File unsupported = File.createTempFile('refresh-failed', '.xml')
        System.setProperty('micronaut.config.files', config.absolutePath)
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] })
        env.start()

        expect:
        env.getProperty('app.main', String).get() == 'one'

        when: "a refresh fails while it reads the configuration files"
        System.setProperty('micronaut.config.files', unsupported.absolutePath)
        env.refresh()

        then: "the properties stay as they were"
        thrown(ConfigurationException)
        env.getProperty('app.main', String).get() == 'one'

        when: "the file has changed by the time the next refresh reads it"
        config.write('app.main=two')
        System.setProperty('micronaut.config.files', config.absolutePath)
        env.refresh()

        then: "nothing read by the failed refresh is left over"
        env.getProperty('app.main', String).get() == 'two'

        cleanup:
        env?.stop()
        config?.delete()
        unsupported?.delete()
    }

    void "the catalog arrays kept for subclasses follow the published catalogs"() {
        given:
        Map<String, Object> data = ['myApp.fooBar': 'old', 'myApp.removed': 'gone']
        CatalogReadingEnvironment env = new CatalogReadingEnvironment()
        env.addPropertySource(new MapPropertySource('data', data))

        when:
        env.start()

        then:
        env.generated('my-app.foo-bar') == 'old'
        env.raw('myApp.fooBar') == 'old'
        env.normalized('my-app.foo-bar') == 'old'

        when: "a property source is added to the running environment"
        env.addPropertySource(PropertySource.of('late', ['zulu.key': 'late']))

        then:
        env.generated('zulu.key') == 'late'
        env.raw('zulu.key') == 'late'
        env.normalized('zulu.key') == 'late'

        when:
        data.put('myApp.fooBar', 'new')
        data.remove('myApp.removed')
        env.refresh()

        then:
        env.generated('my-app.foo-bar') == 'new'
        env.raw('myApp.fooBar') == 'new'
        env.normalized('my-app.foo-bar') == 'new'
        env.generated('my-app.removed') == null
        env.raw('myApp.removed') == null
        env.normalized('my-app.removed') == null
        env.generated('zulu.key') == 'late'

        when:
        env.stop()

        then: "as before, stopping the environment drops the generated catalog only"
        env.generated('my-app.foo-bar') == null
        env.generated('zulu.key') == null
        env.raw('myApp.fooBar') == 'new'
        env.normalized('my-app.foo-bar') == 'new'

        and: "so a stopped environment still resolves what the raw catalog holds"
        !env.getProperty('my-app.foo-bar', String).isPresent()
        env.getProperty('myApp.fooBar', String).get() == 'new'
        env.containsProperty('myApp.fooBar')
    }

    void "a start from another thread during a refresh does not take the read over from it"() {
        given: "an environment whose refresh can be parked between its stop() and its start()"
        CountDownLatch parked = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        AtomicBoolean armed = new AtomicBoolean()
        DefaultEnvironment env = new DefaultEnvironment({ ['test'] } as ApplicationContextConfiguration) {
            @Override
            protected void resetCaches() {
                super.resetCaches()
                if (armed.compareAndSet(true, false)) {
                    parked.countDown()
                    release.await(WAIT_SECONDS, TimeUnit.SECONDS)
                }
            }
        }
        env.addPropertySource(new MapPropertySource('data', ['myApp.fooBar': 'value']))
        env.start()
        Map<String, Object> diff = null
        Thread refresher = new Thread({ diff = env.refreshAndDiff() }, 'refresher')
        Thread starter = new Thread({ env.start() }, 'starter')
        refresher.daemon = true
        starter.daemon = true

        when: "the refresh has stopped the environment and another thread starts it"
        armed.set(true)
        refresher.start()
        assert parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        starter.start()
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            assert starter.state == Thread.State.BLOCKED
        }
        release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        starter.join(WAIT_SECONDS * 1000L)

        then: "the refresh published the properties it read itself: nothing changed"
        !refresher.alive
        !starter.alive
        diff != null
        diff.isEmpty()
        env.getProperty('my-app.foo-bar', String).get() == 'value'

        cleanup:
        release.countDown()
        if (!refresher.alive && !starter.alive) {
            env.stop()
        }
    }

    void "a refresh started by a property source that is being added does not deadlock with another refresh"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder().start()
        Environment env = ctx.environment
        CountDownLatch iterating = new CountDownLatch(1)
        CountDownLatch proceed = new CountDownLatch(1)
        AtomicBoolean refreshOnce = new AtomicBoolean(true)
        PropertySource refreshing = new PropertySource() {
            @Override
            String getName() { 'refreshing' }

            @Override
            Object get(String key) { key == 'extra.key' ? 'value' : null }

            @Override
            Iterator<String> iterator() {
                if (refreshOnce.compareAndSet(true, false)) {
                    iterating.countDown()
                    proceed.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    env.refresh()
                }
                return ['extra.key'].iterator()
            }
        }
        Thread adder = new Thread({ env.addPropertySource(refreshing) }, 'adder')
        Thread refresher = new Thread({
            iterating.await(WAIT_SECONDS, TimeUnit.SECONDS)
            env.refresh()
        }, 'refresher')
        adder.daemon = true
        refresher.daemon = true

        when: "the other refresh is waiting for the lock that the adding thread holds"
        adder.start()
        refresher.start()
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            assert refresher.state == Thread.State.BLOCKED
        }
        proceed.countDown()
        adder.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "it holds no other lock that the adding thread needs for its own refresh"
        !adder.alive
        !refresher.alive
        env.getProperty('extra.key', String).get() == 'value'

        cleanup:
        proceed.countDown()
        if (!adder.alive && !refresher.alive) {
            // closing the context needs the lock that deadlocked threads would still hold
            ctx?.close()
        }
    }

    void "refresh is not declared by DefaultEnvironment itself"() {
        expect: "no bridge method that a subclass compiled against an earlier version, calling super.refresh(), would recurse through"
        DefaultEnvironment.declaredMethods.findAll { it.name == 'refresh' }.empty

        and: "it is the refresh that publishes the catalogs in one step"
        DefaultEnvironment.getMethod('refresh').declaringClass == AtomicRefresh
    }

    /**
     * Runs {@code env.refresh()} on a second thread, waits until that thread is parked inside the
     * gate, that is in the middle of re-reading the property sources, runs the observation on the
     * calling thread, then releases the gate and waits for the refresh to finish.
     */
    private static <T> T refreshThroughGate(Environment env, Gate gate, Closure<T> midRefresh) {
        ExecutorService executor = Executors.newSingleThreadExecutor()
        try {
            gate.arm()
            Future<?> refresh = executor.submit({ env.refresh() } as Callable<Object>)
            assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
            T observed
            try {
                observed = midRefresh.call()
            } finally {
                gate.release.countDown()
            }
            refresh.get(WAIT_SECONDS, TimeUnit.SECONDS)
            return observed
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * A property source without properties that, once armed, parks the thread that iterates it.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final int order
        private final AtomicBoolean armed = new AtomicBoolean()

        Gate(int order) {
            this.order = order
        }

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
            }
            return Collections.emptyIterator()
        }
    }

    static class SlowValue {
        int number
    }

    /**
     * Reads the protected catalog arrays the way a subclass outside of this module could.
     */
    static class CatalogReadingEnvironment extends DefaultEnvironment {

        CatalogReadingEnvironment() {
            super({ ['test'] } as ApplicationContextConfiguration)
        }

        Object generated(String key) {
            valueOf(catalog, key)
        }

        Object raw(String key) {
            valueOf(rawCatalog, key)
        }

        Object normalized(String key) {
            valueOf(nonGenerated, key)
        }

        private static Object valueOf(Map<String, DefaultPropertyEntry>[] entries, String key) {
            entries[((int) key.charAt(0)) - 65]?.get(key)?.value()
        }
    }
}
