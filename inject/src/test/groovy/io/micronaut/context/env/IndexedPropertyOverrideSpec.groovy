package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An indexed property, such as {@code app.list[0]}, that overrides an element of a list or a map
 * another property source declares.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13914")
class IndexedPropertyOverrideSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "an indexed property does not change the list of the property source that declares it"() {
        given:
        List<Object> list = new ArrayList<>(['a', 'b'])
        Map<String, Object> element = new LinkedHashMap<>([name: 'x'])
        List<Object> items = new ArrayList<>([element])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('declares', ['app.list': list, 'app.items': items], 1))
        env.addPropertySource(PropertySource.of('overrides', ['app.list[0]': 'z', 'app.items[0].my-key': 'y'], 2))

        when:
        env.start()

        then:
        env.getProperty('app.list', List).get() == ['z', 'b']
        env.getProperty('app.items', List).get() == [[name: 'x', 'my-key': 'y']]

        and: "the property source still holds what it declares"
        list == ['a', 'b']
        element == [name: 'x']
        items == [[name: 'x']]

        cleanup:
        env.stop()
    }

    void "a list is declared again once the property source that overrides an element of it is removed and the environment refreshed"() {
        given:
        PropertySource overrides = PropertySource.of('overrides', ['app.list[0]': 'z'], 2)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('declares', ['app.list': new ArrayList<>(['a', 'b'])], 1))
        env.addPropertySource(overrides)
        env.start()

        expect:
        env.getProperty('app.list', List).get() == ['z', 'b']

        when:
        env.removePropertySource(overrides)
        env.refresh()

        then:
        env.getProperty('app.list', List).get() == ['a', 'b']

        cleanup:
        env.stop()
    }

    void "an element of an unmodifiable list can be overridden"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('declares', ['app.list': List.of('a', 'b'), 'app.map': Map.of('key', 'a')], 1))
        env.addPropertySource(PropertySource.of('overrides', ['app.list[0]': 'z', 'app.map[key]': 'z'], 2))

        when:
        env.start()

        then:
        env.getProperty('app.list', List).get() == ['z', 'b']
        env.getProperty('app.map', Map).get() == [key: 'z']

        cleanup:
        env.stop()
    }

    void "a lookup made while a refresh rebuilds the catalogs sees a list as it was before the refresh, like any other property"() {
        given: "a property source that overrides an element, and one read after it that parks the refresh once armed"
        Map<String, Object> overriding = ['app.list[0]': 'z', 'app.scalar': 'old']
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('declares', ['app.list': new ArrayList<>(['a', 'b'])], 1))
        env.addPropertySource(new MapPropertySource('overrides', overriding) {
            @Override
            int getOrder() { 2 }
        })
        env.addPropertySource(gate)
        env.start()

        when: "the refresh is parked once it has written the new element"
        overriding.put('app.list[0]', 'y')
        overriding.put('app.scalar', 'new')
        gate.armed.set(true)
        Thread refresher = new Thread({ env.refresh() }, 'refresher')
        refresher.daemon = true
        refresher.start()
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        String scalar = env.getProperty('app.scalar', String).orElse(null)
        List<?> list = env.getProperty('app.list', List).orElse(null)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then:
        scalar == 'old'
        list == ['z', 'b']
        !refresher.alive
        env.getProperty('app.scalar', String).get() == 'new'
        env.getProperty('app.list', List).get() == ['y', 'b']

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
     * A property source without properties, read after the others, that parks the thread that
     * iterates it once armed.
     */
    static class Gate implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        String getName() { 'gate' }

        @Override
        int getOrder() { 3 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                release.await(IndexedPropertyOverrideSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return Collections.emptyIterator()
        }
    }
}
