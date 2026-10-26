package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.order.Ordered
import io.micronaut.core.type.Argument
import io.micronaut.core.value.PropertyResolver
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lookups and {@code Environment#refreshAndDiff()} calls that a refresh overtakes. The threads are
 * sequenced with latches: one is parked inside a property value, a property source or a conversion
 * context while the spec thread acts, and only then is the latch released. The tests about the lock
 * additionally wait until the other thread is blocked on it. Every wait is bounded.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/13915')
@Timeout(60)
class RefreshConcurrentLookupSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    /**
     * What a refresh does to the property that the placeholder of an overtaken lookup names.
     */
    private static final List<List<Object>> REFRESH_KINDS = [
            ['removes', { Map<String, Object> properties -> properties.remove('target') }],
            ['changes', { Map<String, Object> properties -> properties.put('target', 'changed') }]
    ]

    void "a placeholder is resolved from the properties its value was read from when a refresh overtakes the lookup: #lookup, the refresh #kind the property it names"() {
        given: "a value whose text can be parked, that is between reading the value and resolving its placeholder"
        ParkedText oldValue = new ParkedText('${target}')
        Map<String, Object> data = ['app.foo': oldValue, 'target': 'old']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        ExecutorService executor = Executors.newSingleThreadExecutor()

        when: "a refresh replaces the value, and removes or changes the property its placeholder names, while a reader is parked"
        oldValue.arm()
        Future<Object> reader = executor.submit({ read.call(env) } as Callable<Object>)
        assert oldValue.reading.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('app.foo', '${other}')
        change.call(data)
        data.put('other', 'new')
        env.refresh()
        oldValue.release.countDown()

        then: "the reader resolves the placeholder it has read, from the properties it has read it from"
        reader.get(WAIT_SECONDS, TimeUnit.SECONDS) == 'old'

        and: "the next lookup of the same thread, like any other, resolves the refreshed value"
        executor.submit({ read.call(env) } as Callable<Object>).get(WAIT_SECONDS, TimeUnit.SECONDS) == 'new'
        read.call(env) == 'new'

        cleanup:
        oldValue.release.countDown()
        executor.shutdownNow()
        env.stop()

        where: "without the fix a lookup fails when the property is removed, and returns its new value when it is changed"
        [lookup, read, kind, change] << [
                [
                        ['getProperty', { DefaultEnvironment e -> e.getProperty('app.foo', String).get() }],
                        ['getProperty as Map', { DefaultEnvironment e -> e.getProperty('app', Map).get().foo }],
                        ['getProperty as Properties', { DefaultEnvironment e -> e.getProperty('app', Properties).get().getProperty('foo') }],
                        ['getProperty as PropertyResolver', { DefaultEnvironment e -> e.getProperty('app', PropertyResolver).get().getProperty('foo', String).get() }],
                        ['getProperties', { DefaultEnvironment e -> e.getProperties('app').foo }],
                        // not on the Environment: made on the resolver the environment delegates to
                        ['getAllProperties', { DefaultEnvironment e -> resolverOf(e).getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT)['app.foo'] }]
                ],
                REFRESH_KINDS
        ].combinations().collect { it[0] + it[1] }
    }

    void "the values under a prefix are resolved from the properties they were read from when a refresh overtakes the lookup: #lookup, the refresh #kind the property a value names"() {
        given: "a lookup that can be parked once it has read the entries under the prefix, before it resolves their values"
        Park park = new Park()
        Map<String, Object> data = ['app.foo': '${target}', 'target': 'old']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        ExecutorService executor = Executors.newSingleThreadExecutor()

        when: "a refresh replaces the value, and removes or changes the property its placeholder names, while a reader is parked"
        park.arm()
        Future<Object> reader = executor.submit({ read.call(env, park) } as Callable<Object>)
        assert park.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('app.foo', '${other}')
        change.call(data)
        data.put('other', 'new')
        env.refresh()
        park.release.countDown()

        then: "the reader resolves the entries it has read, from the properties it has read them from"
        reader.get(WAIT_SECONDS, TimeUnit.SECONDS) == 'old'

        and: "the next lookup of the same thread, like any other, resolves the refreshed value"
        executor.submit({ read.call(env, park) } as Callable<Object>).get(WAIT_SECONDS, TimeUnit.SECONDS) == 'new'
        read.call(env, park) == 'new'

        cleanup:
        park.release.countDown()
        executor.shutdownNow()
        env.stop()

        where: "the lookups are parked where they read how to build the result"
        [lookup, read, kind, change] << [
                [
                        ['getProperty as Map', { DefaultEnvironment e, Park p -> e.getProperty('app', parkedContext(Map, p)).get().foo }],
                        ['getProperty as Properties', { DefaultEnvironment e, Park p -> e.getProperty('app', parkedContext(Properties, p)).get().getProperty('foo') }],
                        ['getProperty as PropertyResolver', { DefaultEnvironment e, Park p -> e.getProperty('app', parkedContext(PropertyResolver, p)).get().getProperty('foo', String).get() }]
                ],
                REFRESH_KINDS
        ].combinations().collect { it[0] + it[1] }
    }

    void "the difference a refresh reports is that of the refresh alone: a property source added while it is taken holds a key of #catalogMap"() {
        given: "a changed value that parks the refresh when the difference compares it, that is once the refresh has published"
        Map<String, Object> data = ['myApp.fooBar': 'old', 'myApp.other': 'same']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        ParkedValue changed = new ParkedValue('new')
        Map<String, Object> diff = null
        Throwable failure = null
        Thread refresher = new Thread({
            try {
                diff = env.refreshAndDiff()
            } catch (Throwable t) {
                failure = t
            }
        }, 'refresher')
        Thread adder = new Thread({ env.addPropertySource(PropertySource.of('late', [(lateKey): 'late'])) }, 'adder')
        refresher.daemon = true
        adder.daemon = true

        when: "another thread adds a property source while the difference is taken"
        data.put('myApp.fooBar', changed)
        changed.arm()
        refresher.start()
        assert changed.comparing.await(WAIT_SECONDS, TimeUnit.SECONDS)
        adder.start()
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            assert adder.state == Thread.State.BLOCKED || !adder.alive
        }
        changed.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        adder.join(WAIT_SECONDS * 1000L)

        then: "it is added once the difference has been taken, and is not counted as a change of the refresh"
        !refresher.alive
        !adder.alive
        failure == null
        diff == ['my-app.foo-bar': 'old']
        env.getProperty('my-app.foo-bar', String).get() == 'new'
        env.getProperty(lateKey, String).get() == 'late'

        cleanup:
        changed.release.countDown()
        if (!refresher.alive && !adder.alive) {
            env.stop()
        }

        where: "without the fix a key of another catalog map is reported as a change, and a key of the map being compared fails the comparison with a ConcurrentModificationException"
        catalogMap               | lateKey
        'another catalog map'    | 'zulu.key'
        'the map being compared' | 'my-other.key'
    }

    void "each of two overlapping refreshes reports the change it has made"() {
        given: "a refresh that can be parked once it has read the properties, before it publishes them"
        Map<String, Object> data = ['flag.value': 'a']
        Gate gate = new Gate(Ordered.LOWEST_PRECEDENCE)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()
        Map<String, Object> first = null
        Map<String, Object> second = null
        Thread firstRefresh = new Thread({ first = env.refreshAndDiff() }, 'first-refresh')
        Thread secondRefresh = new Thread({ second = env.refreshAndDiff() }, 'second-refresh')
        firstRefresh.daemon = true
        secondRefresh.daemon = true

        when: "a second refresh starts while the first is parked, and the value is set back before the second reads it"
        data.put('flag.value', 'b')
        gate.arm()
        firstRefresh.start()
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        secondRefresh.start()
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            // waits, at most two seconds, for the read lock the first holds
            assert secondRefresh.state == Thread.State.TIMED_WAITING
        }
        data.put('flag.value', 'a')
        gate.release.countDown()
        firstRefresh.join(WAIT_SECONDS * 1000L)
        secondRefresh.join(WAIT_SECONDS * 1000L)

        then: "the first reports the change to b, and the second the change back, which no one would report otherwise"
        !firstRefresh.alive
        !secondRefresh.alive
        first == ['flag.value': 'a']
        second == ['flag.value': 'b']
        env.getProperty('flag.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        if (!firstRefresh.alive && !secondRefresh.alive) {
            env.stop()
        }
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13895')
    void "a refreshAndDiff after one that failed reports the changes since the last published refresh, then none: #failure"() {
        given: "a property source that fails the refresh that reads it, once armed"
        AtomicBoolean fail = new AtomicBoolean()
        Throwable readFailure = failure
        Map<String, Object> data = ['flag.value': 'a']
        PropertySource failing = new PropertySource() {
            @Override
            String getName() { 'failing' }

            @Override
            Object get(String key) { null }

            @Override
            Iterator<String> iterator() {
                if (fail.get()) {
                    throw readFailure
                }
                return Collections.emptyIterator()
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(failing)
        env.start()

        when: "a refreshAndDiff fails while it reads the property sources"
        data.put('flag.value', 'b')
        fail.set(true)
        env.refreshAndDiff()

        then: "the failure is propagated and the properties stay as they were"
        Throwable e = thrown(failure.class)
        e.is(failure)
        env.getProperty('flag.value', String).get() == 'a'

        when: "the next refreshAndDiff succeeds, made on another thread so that it needs the lock the failed one held"
        fail.set(false)
        Map<String, Object> changes = onAnotherThread { env.refreshAndDiff() }

        then: "it reports the change that the failed one did not publish"
        changes == ['flag.value': 'a']
        env.getProperty('flag.value', String).get() == 'b'

        when: "the environment is refreshed once more"
        changes = onAnotherThread { env.refreshAndDiff() }

        then: "nothing has changed since"
        changes == [:]
        env.getProperty('flag.value', String).get() == 'b'

        cleanup:
        env.stop()

        where:
        failure << [new IllegalStateException('failed to read the property source'),
                    new AssertionError((Object) 'failed to read the property source')]
    }

    /**
     * Runs the work on a thread of its own and returns its result, failing rather than waiting
     * longer than the other waits of this spec when the work does not complete.
     */
    private static <T> T onAnotherThread(Closure<T> work) {
        T result = null
        Throwable failure = null
        Thread thread = new Thread({
            try {
                result = work.call()
            } catch (Throwable t) {
                failure = t
            }
        }, 'another-thread')
        thread.daemon = true
        thread.start()
        thread.join(WAIT_SECONDS * 1000L)
        assert !thread.alive: 'the work did not complete'
        if (failure != null) {
            throw failure
        }
        return result
    }

    /**
     * The configuration of an environment that holds the properties a test gives it and nothing of
     * the machine it runs on, which a lookup of a whole prefix, or of every property, reads as well.
     */
    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * The resolver a DefaultEnvironment delegates to.
     */
    private static PropertySourcePropertyResolver resolverOf(DefaultEnvironment env) {
        return (PropertySourcePropertyResolver) env.delegate()
    }

    /**
     * A conversion context for a lookup of the given type that passes the park when the lookup
     * reads from it how to build its result.
     */
    private static <T> ArgumentConversionContext<T> parkedContext(Class<T> type, Park park) {
        Argument<T> argument = Argument.of(type)
        return new ArgumentConversionContext<T>() {
            @Override
            Argument<T> getArgument() { argument }

            @Override
            AnnotationMetadata getAnnotationMetadata() {
                park.pass()
                return AnnotationMetadata.EMPTY_METADATA
            }
        }
    }

    /**
     * Parks, once armed, the thread that passes it.
     */
    static class Park {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()

        void arm() {
            armed.set(true)
        }

        void pass() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the park was never released')
                }
            }
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
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(RefreshConcurrentLookupSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property value that, once armed, parks the thread that reads its text.
     */
    static class ParkedText implements CharSequence {
        final CountDownLatch reading = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String text
        private final AtomicBoolean armed = new AtomicBoolean()

        ParkedText(String text) {
            this.text = text
        }

        void arm() {
            armed.set(true)
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
                reading.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the text was never released')
                }
            }
            return text
        }
    }

    /**
     * A property value that, once armed, parks the thread that compares it with another value, as
     * the difference of a refresh does with a value that the refresh has changed.
     */
    static class ParkedValue {
        final CountDownLatch comparing = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String text
        private final AtomicBoolean armed = new AtomicBoolean()

        ParkedValue(String text) {
            this.text = text
        }

        void arm() {
            armed.set(true)
        }

        @Override
        boolean equals(Object other) {
            if (armed.compareAndSet(true, false)) {
                comparing.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the value was never released')
                }
            }
            return other instanceof ParkedValue && ((ParkedValue) other).text == text
        }

        @Override
        int hashCode() { text.hashCode() }

        @Override
        String toString() { text }
    }
}
