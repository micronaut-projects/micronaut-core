package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.naming.conventions.StringConvention
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * An indexed key, such as {@code app.list[0]}, writes into the list or map that the catalogs hold
 * under its base name. That list or map can be the value of a property source, or one that a
 * published catalog holds, so it is copied before it is written into.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/13914')
@Timeout(60)
class IndexedPropertyCopyOnWriteSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "an indexed key does not change the list of the property source that declares it, and removing it gives the declared list back on refresh"() {
        given:
        List<Object> list = new ArrayList<>(['a', 'b'])
        PropertySource overriding = PropertySource.of('b', ['app.list[0]': 'z'], 2)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('a', ['app.list': list], 1))
        env.addPropertySource(overriding)

        when:
        env.start()

        then:
        env.getRequiredProperty('app.list', List) == ['z', 'b']
        list == ['a', 'b']

        when: "the environment is refreshed twice"
        2.times { env.refresh() }

        then:
        env.getRequiredProperty('app.list', List) == ['z', 'b']
        list == ['a', 'b']

        when: "the overriding property source is removed and the environment refreshed"
        env.removePropertySource(overriding)
        env.refresh()

        then:
        env.getRequiredProperty('app.list', List) == ['a', 'b']
        list == ['a', 'b']

        cleanup:
        env.stop()
    }

    void "an indexed key overrides an element of an immutable list"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.list': List.of('a', 'b')], 1),
                PropertySource.of('b', ['app.list[0]': 'z'], 2)
        )

        expect:
        resolver.getRequiredProperty('app.list', List) == ['z', 'b']
    }

    void "RAW keeps the spelling of a map key under an index however often the property source is processed"() {
        given:
        Map<String, Object> element = new LinkedHashMap<>([name: 'x'])
        List<Object> items = new ArrayList<>([element])
        Map<String, Object> values = new LinkedHashMap<>()
        values.put('app.items', items)
        values.put('app.items[0].myKey', 'y')
        PropertySource source = PropertySource.of('a', values)

        when: "two resolvers process the same property source"
        PropertySourcePropertyResolver first = new PropertySourcePropertyResolver(source)
        PropertySourcePropertyResolver second = new PropertySourcePropertyResolver(source)

        then:
        first.getProperties('app', StringConvention.RAW).items == [[name: 'x', myKey: 'y']]
        second.getProperties('app', StringConvention.RAW).items == [[name: 'x', myKey: 'y']]
        second.getProperties('app').items == [[name: 'x', 'my-key': 'y']]
        items == [[name: 'x']]

        when: "an environment that holds it is refreshed"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(source)
        env.start()
        2.times { env.refresh() }

        then:
        env.getProperties('app', StringConvention.RAW).items == [[name: 'x', myKey: 'y']]
        items == [[name: 'x']]

        cleanup:
        env?.stop()
    }

    void "the keys under an overridden element see the override, as they did when the element was written in place"() {
        given: "a list of maps with a nested list, and overrides of a nested element and of a map key"
        List<Object> tags = new ArrayList<>(['t1', 't2'])
        Map<String, Object> element = new LinkedHashMap<>([name: 'x', tags: tags])
        List<Object> items = new ArrayList<>([element])
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.items': items], 1),
                PropertySource.of('b', ['app.items[0].tags[1]': 'q', 'app.items[0].name': 'w'], 2)
        )

        expect:
        resolver.getRequiredProperty('app.items', List) == [[name: 'w', tags: ['t1', 'q']]]
        resolver.getRequiredProperty('app.items[0].tags', List) == ['t1', 'q']
        resolver.getRequiredProperty('app.items[0].tags[1]', String) == 'q'
        resolver.getRequiredProperty('app.items[0].name', String) == 'w'

        and: "the property source's values are unchanged"
        items == [[name: 'x', tags: ['t1', 't2']]]
        tags == ['t1', 't2']
    }

    void "a map set at an indexed key sees a later key under it, without being changed itself"() {
        given:
        Map<String, Object> element = new LinkedHashMap<>([name: 'x'])
        Map<String, Object> values = new LinkedHashMap<>()
        values.put('app.items[0]', element)
        values.put('app.items[0].myKey', 'y')
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of('a', values))

        expect:
        resolver.getRequiredProperty('app.items[0]', Map) == [name: 'x', 'my-key': 'y']
        resolver.getRequiredProperty('app.items', List) == [[name: 'x', 'my-key': 'y']]
        resolver.getProperties('app', StringConvention.RAW).items == [[name: 'x', myKey: 'y']]
        element == [name: 'x']
    }

    void "the entry of an overridden list keeps the property and the origin it was set from"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.list': ['a', 'b']], 1),
                PropertySource.of('b', ['app.list[0]': 'z'], 2)
        )

        when:
        DefaultPropertyEntry entry = resolver.resolveEntriesForKey('app.list', false, null).get('app.list')

        then:
        entry.value() == ['z', 'b']
        entry.raw() == 'app.list'
        entry.origin().location() == 'a'
    }

    void "a lookup during a refresh sees a list as it was before the refresh, like any other property"() {
        given: "a list and an override of one of its elements, and a property source that parks the refresh after them"
        Map<String, Object> overrides = ['app.list[0]': 'z', 'app.scalar': 'old']
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('a', ['app.list': new ArrayList<>(['a', 'b'])], 1))
        env.addPropertySource(new MapPropertySource('b', overrides) {
            @Override
            int getOrder() { 2 }
        })
        env.addPropertySource(gate)
        env.start()
        Thread refresher = new Thread({ env.refresh() }, 'refresher')
        refresher.daemon = true

        when: "the refresh has processed new values for both, and is parked"
        overrides.put('app.list[0]', 'y')
        overrides.put('app.scalar', 'new')
        gate.arm()
        refresher.start()
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        String scalarDuring = env.getRequiredProperty('app.scalar', String)
        List listDuring = env.getRequiredProperty('app.list', List)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "both are as they were before the refresh"
        !refresher.alive
        scalarDuring == 'old'
        listDuring == ['z', 'b']

        and: "both are as the refresh read them once it has published"
        env.getRequiredProperty('app.scalar', String) == 'new'
        env.getRequiredProperty('app.list', List) == ['y', 'b']

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on.
     */
    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
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
        int getOrder() { 3 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(IndexedPropertyCopyOnWriteSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
            }
            return Collections.emptyIterator()
        }
    }
}
