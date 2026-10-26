package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A property source whose code adds another property source while it is processed. 5.2.x
 * processed both into the published catalogs in place: the added one saw what the other had
 * processed so far, and the writes kept the order they were made in.
 */
@Timeout(60)
class NestedAddSpec extends Specification {

    static final int WAIT_SECONDS = 10

    void "a property source added while another one is added sees what that one has processed so far"() {
        given: "a property source that adds another one once it has given a property, whose value the added one reads"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.start()
        PropertySource child = new LazyValue('child', 'child.value', { env.getProperty('earlier', String).orElse('missing') })
        PropertySource outer = new Scripted('outer', ['earlier', 'child.trigger'], { String key ->
            if (key == 'earlier') {
                return 'value'
            }
            env.addPropertySource(child)
            return 'triggered'
        })

        when:
        env.addPropertySource(outer)

        then:
        env.getProperty('child.value', String).get() == 'value'
        env.getProperty('earlier', String).get() == 'value'

        cleanup:
        env.stop()
    }

    void "a property source added while another one is added keeps the order of the writes: what the other one gives after it wins"() {
        given: "a property source whose value of a key adds another one that sets the same key, and is then given"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.start()
        PropertySource inner = PropertySource.of('inner', ['shared': 'inner', 'inner.only': 'yes'])
        PropertySource outer = new Scripted('outer', ['shared', 'outer.only'], { String key ->
            if (key == 'shared') {
                env.addPropertySource(inner)
                return 'outer'
            }
            return 'yes'
        })

        when:
        env.addPropertySource(outer)

        then:
        env.getProperty('shared', String).get() == 'outer'
        env.getProperty('inner.only', String).get() == 'yes'
        env.getProperty('outer.only', String).get() == 'yes'

        cleanup:
        env.stop()
    }

    void "a property source added by the code of one a refresh processes, and removed before the refresh ends, is not in what the refresh publishes"() {
        given: "a registered property source that, once armed, adds another one while the refresh processes it, and a gate that then parks the refresh"
        AtomicBoolean addChild = new AtomicBoolean()
        PropertySource child = PropertySource.of('child', ['child.value': 'child'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        PropertySource outer = new Scripted('outer', ['outer.value'], { String key ->
            if (addChild.compareAndSet(true, false)) {
                env.addPropertySource(child)
            }
            return 'outer'
        })
        Gate gate = new Gate()
        env.addPropertySource(outer)
        env.addPropertySource(gate)
        env.start()

        when: "the refresh processes the registered one, which adds the other, and the other is removed while the refresh is parked"
        addChild.set(true)
        gate.arm()
        Thread refresher = new Thread({ env.refresh() }, 'refresher')
        refresher.daemon = true
        refresher.start()
        assert gate.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        String duringRefresh = env.getProperty('child.value', String).orElse(null)
        env.removePropertySource(child)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "it was visible once added, and the refresh publishes what it processed itself, without it"
        !refresher.alive
        duringRefresh == 'child'
        !env.containsProperty('child.value')
        env.getProperty('outer.value', String).get() == 'outer'

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
     * A property source with the given keys, whose values the given closure gives when asked.
     */
    static class Scripted implements PropertySource {
        private final String name
        private final List<String> keys
        private final Closure<Object> values

        Scripted(String name, List<String> keys, Closure<Object> values) {
            this.name = name
            this.keys = keys
            this.values = values
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { values.call(key) }

        @Override
        Iterator<String> iterator() { keys.iterator() }
    }

    /**
     * A property source with one key, whose value the given closure computes when asked.
     */
    static class LazyValue implements PropertySource {
        private final String name
        private final String key
        private final Closure<Object> value

        LazyValue(String name, String key, Closure<Object> value) {
            this.name = name
            this.key = key
            this.value = value
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { key == this.key ? value.call() : null }

        @Override
        Iterator<String> iterator() { [key].iterator() }
    }

    /**
     * A property source without properties, processed after the others, that parks the thread that
     * iterates it, once armed.
     */
    static class Gate implements PropertySource {
        final CountDownLatch reached = new CountDownLatch(1)
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
                reached.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                release.await(NestedAddSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return Collections.emptyIterator()
        }
    }
}
