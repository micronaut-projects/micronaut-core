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
 * A refresh that fails puts back the property sources it started with. A property source that
 * another thread adds, replaces or removes while it runs must not be undone by that. The refresh
 * is parked inside a property source that it processes last while another thread changes the
 * property sources, and is then released and failed. Every wait is bounded.
 */
@Timeout(60)
class FailedRefreshPropertySourceChangeSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source added while a refresh that fails runs stays registered, and the next refresh reads it: #failure"() {
        given:
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

        when: "a property source is added while a refresh runs, and the refresh then fails"
        data.put('app.value', 'b')
        fail.set(true)
        Throwable refreshFailure = runWhileParked(env, gate) {
            env.addPropertySource(PropertySource.of('late', ['late.key': 'late']))
        }

        then: "the refresh fails and the properties it read are not published"
        refreshFailure.is(failure)
        env.getProperty('app.value', String).get() == 'a'

        and: "the property source added meanwhile is still registered"
        env.propertySources*.name.contains('late')

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "it reads the added property source with the others"
        env.getProperty('app.value', String).get() == 'b'
        env.getProperty('late.key', String).get() == 'late'

        when: "a property source is added once the refresh has finished"
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

    void "a property source removed while a refresh that fails runs stays removed"() {
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

        when: "a property source is removed while a refresh runs, and the refresh then fails"
        fail.set(true)
        Throwable refreshFailure = runWhileParked(env, gate) {
            env.removePropertySource(extra)
        }

        then: "the failed refresh does not register it again"
        refreshFailure instanceof IllegalStateException
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

    void "a property source replaced while a refresh that fails runs keeps the replacement"() {
        given: "a property source that is replaced by one of the same name, as a configuration watch does"
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('remote', ['app.value': 'v1'], 10))
        env.addPropertySource(gate)
        env.start()
        PropertySource replacement = PropertySource.of('remote', ['app.value': 'v2'], 10)

        when: "it is replaced while a refresh runs, and the refresh then fails"
        fail.set(true)
        Throwable refreshFailure = runWhileParked(env, gate) {
            env.addPropertySource(replacement)
        }

        then: "the replacement stays registered"
        refreshFailure instanceof IllegalStateException
        env.propertySources.find { it.name == 'remote' }.is(replacement)

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then:
        env.getProperty('app.value', String).get() == 'v2'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "property sources added, and one of them replaced, while a refresh that fails runs keep the order they were added in"() {
        given: "property sources of the same order that set the same key, so that the one processed last gives its value"
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a']))
        env.addPropertySource(gate)
        env.start()

        when: "A and then B are added, and A is replaced, while a refresh runs that then fails"
        fail.set(true)
        Throwable refreshFailure = runWhileParked(env, gate) {
            env.addPropertySource(PropertySource.of('first', ['shared.key': 'first']))
            env.addPropertySource(PropertySource.of('second', ['shared.key': 'second']))
            env.addPropertySource(PropertySource.of('first', ['shared.key': 'first-replaced']))
        }

        then: "the replacement keeps the place of the property source it replaced"
        refreshFailure instanceof IllegalStateException
        env.propertySources*.name.findAll { it in ['first', 'second'] } == ['first', 'second']

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "the property sources are processed in that order, as without the failure"
        env.getProperty('shared.key', String).get() == 'second'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source removed and added again while a refresh that fails runs goes after the others"() {
        given: "property sources of the same order that set the same key, so that the one processed last gives its value"
        AtomicBoolean fail = new AtomicBoolean()
        Gate gate = new Gate({
            if (fail.get()) {
                throw new IllegalStateException('failed to read the property source')
            }
        })
        PropertySource first = PropertySource.of('first', ['shared.key': 'first'])
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(first)
        env.addPropertySource(PropertySource.of('second', ['shared.key': 'second']))
        env.addPropertySource(gate)
        env.start()

        expect:
        env.getProperty('shared.key', String).get() == 'second'

        when: "the first is removed and added again while a refresh runs that then fails"
        fail.set(true)
        Throwable refreshFailure = runWhileParked(env, gate) {
            env.removePropertySource(first)
            env.addPropertySource(PropertySource.of('first', ['shared.key': 'first-again']))
        }

        then: "it is registered after the others, as an added property source is"
        refreshFailure instanceof IllegalStateException
        env.propertySources*.name.findAll { it in ['first', 'second'] } == ['second', 'first']

        when: "the next refresh succeeds"
        fail.set(false)
        env.refresh()

        then: "it is processed after the other, as without the failure"
        env.getProperty('shared.key', String).get() == 'first-again'

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    /**
     * Refreshes on another thread, runs the change while the refresh is parked in the gate, then
     * releases it and returns what the refresh threw.
     */
    private static Throwable runWhileParked(DefaultEnvironment env, Gate gate, Closure<?> change) {
        Throwable refreshFailure = null
        gate.arm()
        Thread refresher = daemon('refresher') {
            try {
                env.refresh()
            } catch (Throwable t) {
                refreshFailure = t
            }
        }
        assert gate.entered.await(WAIT_SECONDS, TimeUnit.SECONDS)
        Thread changer = daemon('changer', change)
        changer.join(WAIT_SECONDS * 1000L)
        assert !changer.alive
        gate.release.countDown()
        refresher.join(WAIT_SECONDS * 1000L)
        assert !refresher.alive
        return refreshFailure
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
     * A property source without properties, processed after the others, that once armed parks the
     * thread that iterates it and then runs the given action.
     */
    static class Gate implements PropertySource {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()
        private final Runnable afterRelease

        Gate(Runnable afterRelease) {
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
                if (!release.await(FailedRefreshPropertySourceChangeSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the gate was never released')
                }
                afterRelease.run()
            }
            return Collections.emptyIterator()
        }
    }
}
