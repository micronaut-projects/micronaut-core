package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A lookup that iterates the properties on another thread while the environment starts. The start
 * is parked in a property source processed after the first one, and the lookup in the middle of its
 * iteration by a value whose text parks the thread that reads it, once armed.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14175')
@Timeout(60)
class StartWhileLookingUpSpec extends Specification {

    static final int WAIT_SECONDS = 10

    void "#lookup, parked while it iterates during a start after stop(), returns the properties from before the start"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        Map<String, Object> gatedValues = ordered('app.third': 'three')
        GatedSource gated = new GatedSource('gated', gatedValues, 2)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': parked, 'app.second': 'two'), 1), gated)
        env.start()
        env.stop()
        gatedValues.put('app.fourth', 'four')
        Object before = call(env)

        when: "the start writes keys of the same first letter, one of them new, while the lookup iterates"
        Lookup looking = lookupWhileStartWrites(env, gated, parked, { call(env) })

        then: "the lookup returns what it returned before the start"
        looking.failure == null
        looking.result == before

        and: "later lookups find the started properties"
        call(env) == after

        cleanup:
        gated.release.countDown()
        parked.release.countDown()
        env.stop()

        where:
        lookup                          | call                                                               | after
        "getProperty(name, Map)"        | { DefaultEnvironment e -> e.getProperty('app', Map).get() }        | [first: 'one', second: 'two', third: 'three', fourth: 'four']
        "getProperty(name, Properties)" | { DefaultEnvironment e -> e.getProperty('app', Properties).get() } | props(first: 'one', second: 'two', third: 'three', fourth: 'four')
        "getProperties(name)"           | { DefaultEnvironment e -> e.getProperties('app') }                 | [first: 'one', second: 'two', third: 'three', fourth: 'four']
        "getAllProperties"              | { DefaultEnvironment e -> allProperties(e) }                       | ['app.first': 'one', 'app.second': 'two', 'app.third': 'three', 'app.fourth': 'four']
    }

    void "a lookup that iterates the properties during the first start sees none of them until the start has read them all"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        GatedSource gated = new GatedSource('gated', ['app.third': 'three'], 2)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': parked, 'app.second': 'two'), 1), gated)

        when:
        Lookup looking = lookupWhileStartWrites(env, gated, parked, { env.getProperty('app', Map).get() })

        then:
        looking.failure == null
        looking.result == [:]
        env.getProperty('app', Map).get() == [first: 'one', second: 'two', third: 'three']

        cleanup:
        gated.release.countDown()
        parked.release.countDown()
        env.stop()
    }

    void "a lookup that iterates the properties while an application context starts does not fail"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        GatedSource gated = new GatedSource('qgated', ['qstart.third': 'three'], 2)
        ApplicationContext context = ApplicationContext.builder()
                .deduceEnvironment(false)
                .bootstrapEnvironment(false)
                .propertySources(PropertySource.of('qdata', ordered('qstart.first': parked, 'qstart.second': 'two'), 1), gated)
                .build()
        Environment env = context.environment

        when:
        gated.armed.set(true)
        Lookup starting = new Lookup({ context.start() })
        assert gated.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        parked.armed.set(true)
        Lookup looking = new Lookup({ env.getProperty('qstart', Map).get() })
        assert waitFor({ parked.reached.count == 0 || !looking.thread.alive })
        parked.armed.set(false)
        gated.release.countDown()
        starting.join()
        parked.release.countDown()
        looking.join()

        then:
        env.getClass().simpleName == 'RuntimeConfiguredEnvironment'
        !starting.thread.alive
        starting.failure == null
        !looking.thread.alive
        looking.failure == null
        looking.result == [:]
        env.getProperty('qstart', Map).get() == [first: 'one', second: 'two', third: 'three']

        cleanup:
        gated.release.countDown()
        parked.release.countDown()
        context.stop()
    }

    void "a start that fails keeps the properties it read before it failed, and the environment running"() {
        given:
        FailsOnce failing = new FailsOnce('failing', ['other.key': 'other'], 2)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.first': 'one'], 1), failing)
        failing.armed.set(true)

        when:
        env.start()

        then:
        IllegalStateException e = thrown()
        e.message == 'cannot read failing'
        env.isRunning()
        env.getProperty('app.first', String).orElse(null) == 'one'
        !env.containsProperty('other.key')

        cleanup:
        env.stop()
    }

    void "a property source that runs #nested while a start processes it leaves the properties as that does"() {
        given:
        AtomicBoolean ran = new AtomicBoolean()
        // the anonymous class below does not see the data variable
        String action = nested
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': 'one'), 1))
        PropertySource nesting = new MapPropertySource('nesting', ordered('app.second': 'two', 'app.trigger': 'x', 'app.third': 'three')) {
            @Override
            Object get(String key) {
                if (key == 'app.trigger' && ran.compareAndSet(false, true)) {
                    if (action == 'a refresh') {
                        env.refresh()
                    } else {
                        env.stop()
                        if (action == 'a stop and a start') {
                            env.start()
                        }
                    }
                }
                return super.get(key)
            }

            @Override
            int getOrder() { 2 }
        }
        env.addPropertySource(nesting)

        when:
        env.start()

        then: "a stop drops the generated properties and keeps the raw ones, and the start goes on"
        env.isRunning() == running
        env.getProperty('app', Map).get() == generated
        env.getProperties('app', StringConvention.RAW) == [first: 'one', second: 'two', trigger: 'x', third: 'three']

        cleanup:
        env.stop()

        where:
        nested               | running | generated
        'a stop'             | false   | [trigger: 'x', third: 'three']
        'a stop and a start' | true    | [first: 'one', second: 'two', trigger: 'x', third: 'three']
        'a refresh'          | true    | [first: 'one', second: 'two', trigger: 'x', third: 'three']
    }

    void "a property source sees the properties that the start has read so far"() {
        given:
        Map<String, Object> seen = [:]
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.first': 'one'], 1))
        PropertySource reading = new MapPropertySource('reading', ordered('app.second': 'two', 'app.third': 'three')) {
            @Override
            Object get(String key) {
                if (key == 'app.third') {
                    seen.put('app.first', env.getProperty('app.first', String).orElse(null))
                    seen.put('app.second', env.getProperty('app.second', String).orElse(null))
                }
                return super.get(key)
            }

            @Override
            int getOrder() { 2 }
        }
        env.addPropertySource(reading)

        when:
        env.start()

        then:
        seen == ['app.first': 'one', 'app.second': 'two']
        env.getProperty('app.third', String).get() == 'three'

        cleanup:
        env.stop()
    }

    void "the catalogs kept for subclasses hold what a start after stop() read"() {
        given:
        Map<String, Object> values = ['app.first': 'one']
        DefaultEnvironment env = environmentWith(PropertySource.of('data', values, 1))
        int slot = 'a'.charAt(0) - 65
        env.start()
        env.stop()
        values.put('app.second', 'two')

        when:
        env.start()

        then:
        env.catalog[slot].containsKey('app.second')
        env.rawCatalog[slot].containsKey('app.second')
        env.nonGenerated[slot].containsKey('app.second')

        cleanup:
        env.stop()
    }

    void "a random value that a property source reads while a start after stop() processes it is the value that lookups return after it"() {
        given:
        String[] read = new String[1]
        AtomicBoolean reading = new AtomicBoolean()
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.id': '${random.uuid}'], 1))
        PropertySource readingSource = new MapPropertySource('reading', ['other.key': 'other']) {
            @Override
            Object get(String key) {
                if (reading.get()) {
                    read[0] = env.getProperty('app.id', String).orElse(null)
                }
                return super.get(key)
            }

            @Override
            int getOrder() { 2 }
        }
        env.addPropertySource(readingSource)
        env.start()
        env.stop()
        reading.set(true)

        when:
        env.start()

        then:
        read[0] != null
        env.getProperty('app.id', String).get() == read[0]

        cleanup:
        env.stop()
    }

    private static Lookup lookupWhileStartWrites(DefaultEnvironment env, GatedSource gated, ParkedValue parked, Closure<?> lookup) {
        gated.armed.set(true)
        Lookup starting = new Lookup({ env.start() })
        assert gated.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        parked.armed.set(true)
        Lookup looking = new Lookup(lookup)
        // the lookup is parked on the value, or, if it finds nothing to read, has returned
        assert waitFor({ parked.reached.count == 0 || !looking.thread.alive })
        parked.armed.set(false)
        gated.release.countDown()
        starting.join()
        assert !starting.thread.alive
        assert starting.failure == null
        parked.release.countDown()
        looking.join()
        assert !looking.thread.alive
        return looking
    }

    private static boolean waitFor(Closure<Boolean> condition) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (!condition.call() && System.nanoTime() < end) {
            Thread.sleep(5)
        }
        return condition.call()
    }

    private static DefaultEnvironment environmentWith(PropertySource... propertySources) {
        DefaultEnvironment env = new DefaultEnvironment(ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration)
        for (PropertySource propertySource : propertySources) {
            env.addPropertySource(propertySource)
        }
        return env
    }

    private static Map<String, Object> allProperties(DefaultEnvironment env) {
        return env.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT)
    }

    private static Map<String, Object> ordered(Map<String, Object> values) {
        return new LinkedHashMap<>(values)
    }

    private static Properties props(Map<String, String> values) {
        Properties properties = new Properties()
        properties.putAll(values)
        return properties
    }

    /**
     * Work run on a thread of its own, with its result or failure.
     */
    static class Lookup {
        final Thread thread
        volatile Object result
        volatile Throwable failure

        Lookup(Closure<?> lookup) {
            thread = new Thread({
                try {
                    result = lookup.call()
                } catch (Throwable t) {
                    failure = t
                }
            }, 'lookup')
            thread.daemon = true
            thread.start()
        }

        void join() {
            thread.join(WAIT_SECONDS * 1000L)
        }
    }

    /**
     * A property value whose text, once armed, parks the thread that reads it.
     */
    static final class ParkedValue implements CharSequence {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String text

        ParkedValue(String text) {
            this.text = text
        }

        @Override
        int length() { text.length() }

        @Override
        char charAt(int index) { text.charAt(index) }

        @Override
        CharSequence subSequence(int start, int end) { text.subSequence(start, end) }

        @Override
        String toString() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(StartWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return text
        }
    }

    /**
     * A property source that, once armed, parks the thread that iterates it, which a start does when
     * it processes it.
     */
    static class GatedSource extends MapPropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final int order

        GatedSource(String name, Map<String, Object> values, int order) {
            super(name, values)
            this.order = order
        }

        @Override
        int getOrder() { order }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(StartWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return super.iterator()
        }
    }

    /**
     * A property source that, once armed, fails the next time it is iterated.
     */
    static class FailsOnce extends MapPropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final int order

        FailsOnce(String name, Map<String, Object> values, int order) {
            super(name, values)
            this.order = order
        }

        @Override
        int getOrder() { order }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                throw new IllegalStateException("cannot read ${getName()}")
            }
            return super.iterator()
        }
    }
}
