package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * A property source added to a running environment while the environment reads its property
 * sources, when it starts or when it is refreshed. The read is parked inside a property source
 * that it processes last while another thread adds a property source, and only then released.
 * Every wait is bounded.
 */
@Timeout(60)
class AddPropertySourceDuringReadSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source added while a refresh reads the property sources is in the properties the refresh publishes"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()

        when: "another thread adds a property source while the refresh is parked in the middle of the read"
        gate.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('late', ['late.key': 'late'])) }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the adding thread does not wait for the read, and the property is not visible before the refresh publishes"
        !adder.alive
        !env.getProperty('late.key', String).present

        when:
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh has published the property source with the others"
        !refresher.alive
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('app.value', String).get() == 'a'

        and: "a later refresh keeps it"
        env.refresh().getProperty('late.key', String).get() == 'late'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source replaced while a refresh reads the property sources gives its new values once the refresh has published"() {
        given: "a property source that is replaced by one of the same name and order, as the Consul configuration watch does"
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        env.start()

        when: "it is replaced while the refresh has read the previous one"
        gate.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('remote', ['app.value': 'v2'], 10)) }
        adder.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the published properties hold the new values"
        !adder.alive
        !refresher.alive
        env.getProperty('app.value', String).get() == 'v2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added while a refresh reads, before the read has collected the registered property sources, keeps its order"() {
        given: "a configuration that, once armed, parks the read where it reads the default property sources, before it collects the registered ones"
        Park park = new Park()
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            List<String> getEnvironmentVariableIncludes() {
                park.pass()
                // no environment variable is read
                return ['MICRONAUT_ADD_PROPERTY_SOURCE_DURING_READ_SPEC_NONE']
            }
        })
        env.addPropertySource(PropertySource.of('higher', ['order.value': 'higher'], 100))
        env.start()

        when: "another thread adds a property source of a lower order that sets the same key, while the refresh is parked"
        park.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert park.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('lower', ['order.value': 'lower', 'lower.key': 'lower'], -100)) }
        adder.join(WAIT_SECONDS * 1000L)
        park.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh processes it once, in its order, so the property source of the higher order keeps the key"
        !adder.alive
        !refresher.alive
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('order.value', String).get() == 'higher'

        cleanup:
        park.release.countDown()
        env.stop()
    }

    void "a property source added while the environment starts is in the properties once it has started"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)

        when: "another thread adds a property source while the start is parked in the middle of the read"
        gate.arm()
        Thread starter = daemon('starter') { env.start() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('late', ['late.key': 'late'])) }
        adder.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)

        then:
        !adder.alive
        !starter.alive
        env.getProperty('late.key', String).get() == 'late'
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that a property sources locator adds while the environment reads is in the properties the read publishes"() {
        given: "a locator that adds a property source to the environment rather than returning it"
        PropertySourcesLocator locator = new PropertySourcesLocator() {
            @Override
            Collection<PropertySource> load(Environment environment) {
                environment.addPropertySource(PropertySource.of('located', ['located.key': 'located']))
                return []
            }
        }
        DefaultEnvironment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            boolean isEnableDefaultPropertySources() { false }

            @Override
            Collection<PropertySourcesLocator> getPropertySourcesLocators() { [locator] }
        })

        when:
        env.start()

        then: "it is not left for the next refresh to read"
        env.getProperty('located.key', String).get() == 'located'

        cleanup:
        env.stop()
    }

    void "a property source added while a refresh that fails reads the property sources stays registered, and is in the properties that stay published: #failure"() {
        given: "a property source that, once armed, parks the read and then fails it"
        AtomicBoolean fail = new AtomicBoolean()
        Throwable readFailure = failure
        Gate gate = new Gate({
            if (fail.get()) {
                throw readFailure
            }
        })
        Map<String, Object> data = ['app.value': 'a']
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.addPropertySource(gate)
        env.start()

        when: "a property source is added while a refresh reads, and the refresh then fails"
        data.put('app.value', 'b')
        fail.set(true)
        gate.arm()
        Throwable refreshFailure = null
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread adder = daemon('adder') { env.addPropertySource(PropertySource.of('late', ['late.key': 'late'])) }
        adder.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh fails, and the properties it read are not published"
        !adder.alive
        !refresher.alive
        refreshFailure.is(failure)
        env.getProperty('app.value', String).get() == 'a'

        and: "the property source added meanwhile is registered, and its properties are published, as outside a refresh"
        env.propertySources*.name.contains('late')
        env.getProperty('late.key', String).get() == 'late'

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "it reads the added property source with the others"
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('late.key', String).get() == 'late'

        when: "a property source is added once the refresh has failed"
        env.addPropertySource(PropertySource.of('later', ['later.key': 'later']))

        then: "the environment is no longer reading, so it is processed right away"
        env.getProperty('later.key', String).get() == 'later'

        cleanup:
        gate.release.countDown()
        env.stop()

        where:
        failure << [new IllegalStateException('failed to read the property source'),
                    new AssertionError((Object) 'failed to read the property source')]
    }

    void "a property source removed while a refresh that fails reads the property sources stays removed"() {
        given:
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        PropertySource extra = PropertySource.of('extra', ['extra.key': 'extra'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(extra)
        env.addPropertySource(gate)
        env.start()

        expect:
        env.getProperty('extra.key', String).get() == 'extra'

        when: "a property source is removed while a refresh reads, and the refresh then fails"
        fail.set(true)
        gate.arm()
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread remover = daemon('remover') { env.removePropertySource(extra) }
        remover.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the failed refresh does not register it again"
        !remover.alive
        !refresher.alive
        !env.propertySources*.name.contains('extra')

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "it no longer reads it"
        !env.getProperty('extra.key', String).present

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source added and removed again while a refresh reads the property sources is not processed"() {
        given:
        Gate gate = new Gate()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()
        PropertySource late = PropertySource.of('late', ['late.key': 'late'])

        when: "another thread adds a property source and removes it again while the refresh is parked"
        gate.arm()
        Thread refresher = daemon('refresher') { env.refresh() }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread changer = daemon('changer') {
            env.addPropertySource(late)
            env.removePropertySource(late)
        }
        changer.join(WAIT_SECONDS * 1000L)
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh publishes nothing of it"
        !changer.alive
        !refresher.alive
        !env.getProperty('late.key', String).present
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source replaced in a tight loop while refreshes run neither keeps a refresh from finishing nor piles up"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.start()
        Map<String, PropertySource> handedToRead = env.@addedWhileReading
        AtomicBoolean adding = new AtomicBoolean(true)
        AtomicLong lastAdded = new AtomicLong()
        AtomicInteger mostHandedToRead = new AtomicInteger()
        AtomicInteger refreshes = new AtomicInteger()

        when: "one thread replaces a property source as fast as it can while another refreshes"
        Thread adder = daemon('adder') {
            long i = 0
            while (adding.get()) {
                i++
                env.addPropertySource(PropertySource.of('watch', ['watch.value': i]))
                lastAdded.set(i)
                synchronized (handedToRead) {
                    mostHandedToRead.accumulateAndGet(handedToRead.size(), Math::max)
                }
            }
        }
        Thread refresher = daemon('refresher') {
            200.times {
                env.refresh()
                refreshes.incrementAndGet()
            }
        }
        refresher.join(WAIT_SECONDS * 1000L)
        adding.set(false)
        adder.join(WAIT_SECONDS * 1000L)

        then: "every refresh finishes, and a read holds no more than the last property source added by the name"
        !refresher.alive
        refreshes.get() == 200
        !adder.alive
        mostHandedToRead.get() <= 1

        and: "the last property source added is the one whose properties are published"
        env.getProperty('watch.value', Long).get() == lastAdded.get()
        env.getProperty('app.value', String).get() == 'a'

        cleanup:
        adding.set(false)
        env.stop()
    }

    void "refreshes do not fail while another thread keeps adding and removing a property source"() {
        given: "an environment built as an application builds it, with the config import locator"
        Environment env = ApplicationContext.builder()
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false)
                .propertySources(PropertySource.of('data', ['app.value': 'a']))
                .build()
                .environment
        env.start()
        AtomicBoolean changing = new AtomicBoolean(true)
        PropertySource removeHandle = PropertySource.of('watch', [:])
        List<Throwable> failures = Collections.synchronizedList([])

        when:
        Thread changer = daemon('changer') {
            long i = 0
            while (changing.get()) {
                if (++i % 10 == 0) {
                    env.removePropertySource(removeHandle)
                } else {
                    env.addPropertySource(PropertySource.of('watch', ['watch.value': i]))
                }
            }
        }
        Thread refresher = daemon('refresher') {
            5000.times {
                try {
                    env.refreshAndDiff()
                } catch (Throwable t) {
                    failures.add(t)
                }
            }
        }
        refresher.join(WAIT_SECONDS * 3000L)
        changing.set(false)
        changer.join(WAIT_SECONDS * 1000L)

        then: "no refresh fails on the property sources changing underneath it"
        !refresher.alive
        !changer.alive
        failures.empty

        cleanup:
        changing.set(false)
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

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
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
     * A property source without properties, processed after the others, that once armed parks the
     * thread that iterates it and then runs the given action.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()
        private final Runnable afterRelease

        Gate(Runnable afterRelease = {}) {
            this.afterRelease = afterRelease
        }

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
                afterRelease.run()
            }
            return Collections.emptyIterator()
        }
    }
}
