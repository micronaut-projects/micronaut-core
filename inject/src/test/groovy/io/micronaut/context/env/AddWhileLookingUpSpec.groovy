package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A lookup that iterates the properties while another thread adds a property source to the running
 * environment. The lookup is parked in the middle of its iteration by a value whose text parks the
 * thread that reads it, once armed.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14140')
@Timeout(60)
class AddWhileLookingUpSpec extends Specification {

    static final int WAIT_SECONDS = 10

    void "#lookup, parked while it iterates, returns the properties from before a property source added meanwhile"() {
        given:
        ParkedValue parked = new ParkedValue('one')
        DefaultEnvironment env = startedWith(PropertySource.of('data', ordered('app.first': parked, 'app.second': 'two')))

        when: "another thread adds a key of the same first letter while the lookup iterates"
        parked.armed.set(true)
        Lookup looking = new Lookup({ call(env) })
        assert parked.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(PropertySource.of('added', ['app.third': 'three']))
        parked.release.countDown()
        looking.join()

        then: "the lookup returns what was there before the add"
        looking.failure == null
        looking.result == before

        and: "later lookups find the added property"
        call(env) == after

        cleanup:
        parked.release.countDown()
        env.stop()

        where:
        lookup                        | call                                                         | before                                  | after
        "getProperty(name, Map)"      | { DefaultEnvironment e -> e.getProperty('app', Map).get() }  | [first: 'one', second: 'two']           | [first: 'one', second: 'two', third: 'three']
        "getProperty(name, Properties)" | { DefaultEnvironment e -> e.getProperty('app', Properties).get() } | props(first: 'one', second: 'two') | props(first: 'one', second: 'two', third: 'three')
        "getProperties(name)"         | { DefaultEnvironment e -> e.getProperties('app') }           | [first: 'one', second: 'two']           | [first: 'one', second: 'two', third: 'three']
    }

    void "lookups that iterate only the names do not fail while another thread keeps adding names of the same first letter"() {
        given:
        Map<String, Object> values = [:]
        200.times { values.put("app.key$it".toString(), "v$it") }
        DefaultEnvironment env = startedWith(PropertySource.of('data', values))
        AtomicBoolean adding = new AtomicBoolean(true)
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>()

        when: "one thread looks up while another adds, for a bounded time"
        Thread looking = Thread.start {
            try {
                while (adding.get()) {
                    env.containsProperties('apx')
                    env.getPropertyEntries('app')
                    env.getPropertyPathMatches('app.*')
                }
            } catch (Throwable t) {
                failures.add(t)
            }
        }
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        int added = 0
        while (System.nanoTime() < end && failures.isEmpty()) {
            env.addPropertySource(PropertySource.of("added$added", [("app.added$added".toString()): 'v']))
            added++
        }
        adding.set(false)
        looking.join(WAIT_SECONDS * 1000L)

        then:
        failures.isEmpty()
        env.getPropertyEntries('app').contains('added0'.toString())

        cleanup:
        adding.set(false)
        env.stop()
    }

    void "a property source sees the properties it has added so far while it is added"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('data', ['app.first': 'one']))
        Map<String, Object> seen = [:]
        PropertySource reading = new MapPropertySource('reading', ordered('app.second': 'two', 'app.third': 'three')) {
            @Override
            Object get(String key) {
                if (key == 'app.third') {
                    seen.put('app.second', env.getProperty('app.second', String).orElse(null))
                }
                return super.get(key)
            }
        }

        when:
        env.addPropertySource(reading)

        then:
        seen == ['app.second': 'two']
        env.getProperty('app.third', String).get() == 'three'

        cleanup:
        env.stop()
    }

    void "a random value that a lookup reads while a property source is added is the value that lookups return after it"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('data', ['app.id': '${random.uuid}']))
        ParksOnIteration parking = new ParksOnIteration('added', ['other.key': 'other'])

        when: "a lookup reads the random value while another thread is adding a property source"
        parking.armed.set(true)
        Lookup adding = new Lookup({ env.addPropertySource(parking) })
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        String during = env.getProperty('app.id', String).get()
        parking.release.countDown()
        adding.join()

        then:
        adding.failure == null
        env.getProperty('app.id', String).get() == during
        env.getProperty('other.key', String).get() == 'other'

        cleanup:
        parking.release.countDown()
        env.stop()
    }

    void "a property source that fails while it is added leaves the properties it added before it failed"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('data', ['app.first': 'one']))
        PropertySource failing = new MapPropertySource('failing', ordered('app.second': 'two', 'app.third': 'three')) {
            @Override
            Object get(String key) {
                if (key == 'app.third') {
                    throw new IllegalStateException('cannot read app.third')
                }
                return super.get(key)
            }
        }

        when:
        env.addPropertySource(failing)

        then:
        IllegalStateException e = thrown()
        e.message == 'cannot read app.third'
        env.getProperty('app.second', String).orElse(null) == 'two'
        !env.containsProperty('app.third')

        cleanup:
        env.stop()
    }

    void "a refresh that fails while a property source is added leaves the properties that were there and what the add wrote before it"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('data', ['app.first': 'one']))
        FailsOnce failing = new FailsOnce('failing', ['other.key': 'other'])
        env.addPropertySource(failing)
        AtomicBoolean refreshed = new AtomicBoolean()
        PropertySource refreshing = new MapPropertySource('refreshing', ordered('app.second': 'two', 'app.trigger': 'x')) {
            @Override
            Object get(String key) {
                if (key == 'app.trigger' && refreshed.compareAndSet(false, true)) {
                    failing.armed.set(true)
                    env.refresh()
                }
                return super.get(key)
            }
        }

        when: "a property source refreshes the environment while it is added, and the refresh fails"
        env.addPropertySource(refreshing)

        then:
        IllegalStateException e = thrown()
        e.message == 'cannot read failing'
        env.getProperty('app.first', String).orElse(null) == 'one'
        env.getProperty('other.key', String).orElse(null) == 'other'
        env.getProperty('app.second', String).orElse(null) == 'two'

        cleanup:
        env.stop()
    }

    void "a refresh and diff made while a property source is added reports what the refresh changed"() {
        given:
        Map<String, Object> values = ['app.value': 'old']
        DefaultEnvironment env = startedWith(PropertySource.of('data', values))
        AtomicBoolean refreshed = new AtomicBoolean()
        AtomicReference<Map<String, Object>> diff = new AtomicReference<>()
        PropertySource refreshing = new MapPropertySource('refreshing', ordered('app.second': 'two', 'app.trigger': 'x')) {
            @Override
            Object get(String key) {
                if (key == 'app.trigger' && refreshed.compareAndSet(false, true)) {
                    values.put('app.value', 'new')
                    diff.set(env.refreshAndDiff())
                }
                return super.get(key)
            }
        }

        when:
        env.addPropertySource(refreshing)

        then:
        diff.get().containsKey('app.value')
        env.getProperty('app.value', String).get() == 'new'

        cleanup:
        env.stop()
    }

    void "an element of a list that an added indexed key does not write keeps its own map"() {
        given: "a list whose first element is a case-insensitive map"
        Map<String, Object> server = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        server.put('Host', 'a')
        DefaultEnvironment env = startedWith(PropertySource.of('first', [servers: [server]]))

        when: "an added property source writes the second element"
        env.addPropertySource(PropertySource.of('added', ['servers[1].host': 'b']))

        then: "the first element is still looked up through its own map"
        env.getProperty('servers[0].HOST', String).orElse(null) == 'a'
        env.getProperty('servers[1].host', String).orElse(null) == 'b'

        cleanup:
        env.stop()
    }

    void "a list built from indexed keys keeps the key and origin it was first built from when an added property source adds to it"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('first', ordered('foo[0]': 'a', 'foo[1]': 'b')))

        when:
        env.addPropertySource(PropertySource.of('added', ['foo[2]': 'c']))

        then:
        env.getProperty('foo', List).get() == ['a', 'b', 'c']
        env.getPropertyEntry('foo').get().raw() == 'foo[0]'
        env.getPropertyEntry('foo').get().origin().location() == 'first'

        cleanup:
        env.stop()
    }

    void "a #kind under an indexed key that an added property source writes into is the same through the indexed key and through the list"() {
        given:
        DefaultEnvironment env = startedWith(PropertySource.of('first', ['foo[0]': element]))

        when:
        env.addPropertySource(PropertySource.of('added', [(key): 'x']))

        then:
        env.getProperty('foo', List).get() == [expected]
        env.getProperty('foo[0]', type).get() == expected

        cleanup:
        env.stop()

        where:
        kind   | element    | key          | type | expected
        'list' | ['a', 'b'] | 'foo[0][1]'  | List | ['a', 'x']
        'map'  | [a: 'one'] | 'foo[0].b'   | Map  | [a: 'one', b: 'x']
    }

    private static DefaultEnvironment startedWith(PropertySource propertySource) {
        DefaultEnvironment env = new DefaultEnvironment(ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration)
        env.addPropertySource(propertySource)
        env.start()
        return env
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
     * A lookup run on a thread of its own, with its result or failure.
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
            assert !thread.alive
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
                release.await(AddWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return text
        }
    }

    /**
     * A property source that, once armed, fails the next time it is iterated.
     */
    static class FailsOnce extends MapPropertySource {
        final AtomicBoolean armed = new AtomicBoolean()

        FailsOnce(String name, Map<String, Object> values) {
            super(name, values)
        }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                throw new IllegalStateException("cannot read ${getName()}")
            }
            return super.iterator()
        }
    }

    /**
     * A property source that, once armed, parks the thread that iterates it, which the environment
     * does when it adds it.
     */
    static class ParksOnIteration extends MapPropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        ParksOnIteration(String name, Map<String, Object> values) {
            super(name, values)
        }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                release.await(AddWhileLookingUpSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return super.iterator()
        }
    }
}
