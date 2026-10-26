package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lookups while another thread adds a property source to a running environment. An add builds its
 * catalogs off to the side and publishes them in one step, so a lookup never reads a catalog that
 * is being written. Each lookup is parked at a fixed point while the add runs, and every wait is
 * bounded.
 */
@Timeout(60)
class LookupWhileAddingSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/14140')
    void "a map lookup that iterates the properties while another thread adds a property source does not fail"() {
        given: "a value whose text, once armed, parks the lookup in the middle of iterating the keys that start with 'a'"
        ParkedText parked = new ParkedText('one')
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.first': parked, 'app.second': 'two']))
        env.start()
        Map<String, Object> result = null
        Throwable failure = null
        Thread lookup = new Thread({
            try {
                result = env.getProperty('app', Map).get()
            } catch (Throwable t) {
                failure = t
            }
        }, 'lookup')
        lookup.daemon = true

        when: "another key that starts with 'a' is added while the lookup is parked"
        parked.arm()
        lookup.start()
        assert parked.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        env.addPropertySource(PropertySource.of('added', ['app.third': 'three']))
        parked.release.countDown()
        lookup.join(WAIT_SECONDS * 1000L)

        then: "the lookup returns the properties as they were when it started"
        !lookup.alive
        failure == null
        result == [first: 'one', second: 'two']

        and: "the next lookup sees the added property"
        env.getProperty('app', Map).get() == [first: 'one', second: 'two', third: 'three']

        cleanup:
        parked.release.countDown()
        env.stop()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
    void "a list is not changed by a property source being added until the add publishes it"() {
        given: "a property source that overrides an element of a list, and parks the add on its next key"
        CountDownLatch reached = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        PropertySource overriding = new PropertySource() {
            @Override
            String getName() { 'overriding' }

            @Override
            int getOrder() { 2 }

            @Override
            Object get(String key) {
                if (key == 'app.marker') {
                    reached.countDown()
                    // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                    if (!release.await(LookupWhileAddingSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException('the add was never released')
                    }
                    return 'added'
                }
                return key == 'app.list[0]' ? 'x' : null
            }

            @Override
            Iterator<String> iterator() { ['app.list[0]', 'app.marker'].iterator() }
        }
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('declaring', ['app.list': new ArrayList<>(['a', 'b'])], 1))
        env.start()
        Thread adder = new Thread({ env.addPropertySource(overriding) }, 'adder')
        adder.daemon = true

        when: "the add has processed the element and is parked"
        adder.start()
        assert reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        List<Object> during = env.getRequiredProperty('app.list', List)
        boolean markerDuring = env.containsProperty('app.marker')
        release.countDown()
        adder.join(WAIT_SECONDS * 1000L)

        then: "neither the element nor the other key was visible before the add published them together"
        !adder.alive
        during == ['a', 'b']
        !markerDuring
        env.getRequiredProperty('app.list', List) == ['x', 'b']
        env.getRequiredProperty('app.marker', String) == 'added'

        cleanup:
        release.countDown()
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    /**
     * A property value that, once armed, parks the thread that reads its text.
     */
    static class ParkedText implements CharSequence {
        final CountDownLatch reached = new CountDownLatch(1)
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
                reached.countDown()
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the text was never released')
                }
            }
            return text
        }
    }
}
