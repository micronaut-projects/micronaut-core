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

    void "#lookup during #start returns what it returned before the start"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        GatedSource gated = new GatedSource('gated', ['app.third': 'three'], 2)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': parked, 'app.second': 'two'), 1), gated)
        if (start == 'a start after stop()') {
            env.start()
            env.stop()
        }
        Object before = call(env)

        when: "the start has processed data and writes a key of the same first letter"
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
        [lookup, call, after, start] << [
                ["getProperty(name, Map)", { DefaultEnvironment e -> e.getProperty('app', Map).get() },
                 [first: 'one', second: 'two', third: 'three']],
                ["getProperty(name, Properties)", { DefaultEnvironment e -> e.getProperty('app', Properties).get() },
                 props(first: 'one', second: 'two', third: 'three')],
                ["getProperties(name)", { DefaultEnvironment e -> e.getProperties('app') },
                 [first: 'one', second: 'two', third: 'three']],
                ["getAllProperties", { DefaultEnvironment e -> allProperties(e) },
                 ['app.first': 'one', 'app.second': 'two', 'app.third': 'three']]
        ].collectMany { row -> ['a first start', 'a start after stop()'].collect { row + it } }
    }

    void "#lookup, parked while it iterates during a start that follows a refresh of the stopped environment, returns what it returned before the start"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        Map<String, Object> gatedValues = ordered('app.third': 'three')
        GatedSource gated = new GatedSource('gated', gatedValues, 2)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': parked, 'app.second': 'two'), 1), gated)
        // a refresh of an environment that does not run publishes its properties, which the start then writes over
        env.refresh()
        gatedValues.put('app.fourth', 'four')
        Object before = call(env)

        when: "the start writes a new key of the same first letter while the lookup iterates"
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

    void "a lookup while a start is partway through a property source sees none of the start's properties"() {
        given:
        GatedSource gated = new GatedSource('gated', ordered('app.third': 'three', 'app.fourth': 'four'), 2, 'app.fourth')
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': 'one', 'app.second': 'two'), 1), gated)

        when: "the start has written app.third and parks before app.fourth"
        gated.armed.set(true)
        Lookup starting = new Lookup({ env.start() })
        assert gated.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Lookup looking = new Lookup({ env.getProperty('app', Map).get() })
        looking.join()
        gated.release.countDown()
        starting.join()

        then: "it sees what was there before the start, which is nothing"
        !starting.thread.alive
        starting.failure == null
        !looking.thread.alive
        looking.failure == null
        looking.result == [:]
        env.getProperty('app', Map).get() == [first: 'one', second: 'two', third: 'three', fourth: 'four']

        cleanup:
        gated.release.countDown()
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

        then: "it sees what was there before the start, which is nothing"
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
        FailsAt failing = new FailsAt('failing', ordered('other.first': 'first', 'other.second': 'second'), 2, 'other.second')
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.first': 'one'], 1), failing)
        failing.armed.set(true)

        when:
        env.start()

        then:
        IllegalStateException e = thrown()
        e.message == 'cannot read other.second'
        env.isRunning()
        env.getProperty('app.first', String).orElse(null) == 'one'
        env.getProperty('other.first', String).orElse(null) == 'first'
        !env.containsProperty('other.second')

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
                    env.stop()
                    if (action == 'a stop and a start') {
                        env.start()
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

        then: "a stop drops the properties, and the start goes on"
        env.isRunning() == running
        env.getProperty('app', Map).get() == generated
        env.getProperties('app', StringConvention.RAW) == generated

        cleanup:
        env.stop()

        where:
        nested               | running | generated
        'a stop'             | false   | [trigger: 'x', third: 'three']
        'a stop and a start' | true    | [first: 'one', second: 'two', trigger: 'x', third: 'three']
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

    void "a property source added just before a start reads, and processed between two of the start's property sources, is processed on top, as before"() {
        given:
        ConventionGate later = new ConventionGate('later', ['later.key': 'later'], 3)
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ordered('app.first': 'one', 'app.second': 'two'), 1), later)
        ConventionGate added = new ConventionGate('added', ['app.second': 'added'], 0)
        env.start()

        when: "the add passes the running check and parks before it processes, then the environment restarts"
        added.armed.set(true)
        Lookup adding = new Lookup({ env.addPropertySource(added) })
        assert added.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.stop()
        later.armed.set(true)
        Lookup starting = new Lookup({ env.start() })
        assert later.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        added.release.countDown()
        adding.join()
        later.release.countDown()
        starting.join()

        then: "the start read the added property source in its order, and the add processed it again, on top"
        !adding.thread.alive
        adding.failure == null
        !starting.thread.alive
        starting.failure == null
        env.getProperty('app', Map).get() == [first: 'one', second: 'added']
        env.getProperty('later.key', String).orElse(null) == 'later'

        and: "a refresh reads them in their order"
        env.refresh()
        env.getProperty('app', Map).get() == [first: 'one', second: 'two']

        cleanup:
        added.release.countDown()
        later.release.countDown()
        env.stop()
    }

    private static Lookup lookupWhileStartWrites(DefaultEnvironment env, GatedSource gated, ParkedValue parked, Closure<?> lookup) {
        gated.armed.set(true)
        Lookup starting = new Lookup({ env.start() })
        assert gated.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        parked.armed.set(true)
        Lookup looking = new Lookup(lookup)
        // the lookup is parked on the value, or, if it does not read it, has returned
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
        return ((PropertySourcePropertyResolver) env.delegate()).getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT)
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
     * A property source that, once armed, parks the thread that processes it: before it writes any
     * of its keys, which a start does when it iterates it, or, given a key, once it has written the
     * keys before that one. It is not a {@link MapPropertySource}, whose keys a configuration import
     * reads before the start processes any property source.
     */
    static class GatedSource implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String name
        private final Map<String, Object> values
        private final int order
        private final String gatedKey

        GatedSource(String name, Map<String, Object> values, int order, String gatedKey = null) {
            this.name = name
            this.values = values
            this.order = order
            this.gatedKey = gatedKey
        }

        @Override
        String getName() { name }

        @Override
        int getOrder() { order }

        @Override
        Iterator<String> iterator() {
            if (gatedKey == null) {
                park()
            }
            return values.keySet().iterator()
        }

        @Override
        Object get(String key) {
            if (key == gatedKey) {
                park()
            }
            return values.get(key)
        }

        private void park() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(StartWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    /**
     * A property source that, once armed, parks the thread that asks for its convention. A start asks
     * for it before it takes the lock of the catalogs to process the property source, and an add
     * after it has passed its running check.
     */
    static class ConventionGate implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String name
        private final Map<String, Object> values
        private final int order

        ConventionGate(String name, Map<String, Object> values, int order) {
            this.name = name
            this.values = values
            this.order = order
        }

        @Override
        String getName() { name }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) { values.get(key) }

        @Override
        Iterator<String> iterator() { values.keySet().iterator() }

        @Override
        PropertySource.PropertyConvention getConvention() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(StartWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return PropertySource.PropertyConvention.JAVA_PROPERTIES
        }
    }

    /**
     * A property source that, once armed, fails when the given key is read, after it has written the
     * keys before that one.
     */
    static class FailsAt extends MapPropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final int order
        private final String failingKey

        FailsAt(String name, Map<String, Object> values, int order, String failingKey) {
            super(name, values)
            this.order = order
            this.failingKey = failingKey
        }

        @Override
        int getOrder() { order }

        @Override
        Object get(String key) {
            if (key == failingKey && armed.compareAndSet(true, false)) {
                throw new IllegalStateException("cannot read $key")
            }
            return super.get(key)
        }
    }
}
