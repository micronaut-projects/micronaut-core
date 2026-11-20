package io.micronaut.context.env

import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.core.order.Ordered
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A property source that another thread adds just before a refresh reads the property sources
 * waits for the lock of the catalogs, which the refresh holds. The refresh reads it in its order,
 * and the add then does not process it a second time, on top of the others.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/14159")
@Timeout(60)
class AddBeforeRefreshReadsSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a property source added just before a refresh reads the property sources is processed once, in its order: #trigger"() {
        given: "a property source of a higher order that sets the same key"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)
        Thread adder = null

        when: "it is registered, and its add waits for the lock of the catalogs while the refresh reads the property sources"
        holdingCatalogLock(env) {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder, env, lower)
            trigger == 'refreshAndDiff' ? env.refreshAndDiff() : env.refresh()
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh has processed it in its order, so the property source of the higher order wins"
        !adder.alive
        env.getProperty('shared', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'
        env.propertySources.any { it.is(lower) }

        cleanup:
        env.stop()

        where:
        trigger << ['refresh', 'refreshAndDiff']
    }

    void "a property source that was read, changed and added again is processed again, on top, as before"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        Map<String, Object> values = ['shared': 'first']
        PropertySource changing = PropertySource.of('changing', values, -10)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.addPropertySource(changing)
        env.start()
        env.refresh()

        when:
        values.put('shared', 'changed')
        env.addPropertySource(changing)

        then:
        env.getProperty('shared', String).get() == 'changed'

        cleanup:
        env.stop()
    }

    void "a property source added just before a refresh that then fails is processed once the refresh has failed, as before"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        FailingSource failing = new FailingSource()
        env.addPropertySource(failing)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)
        Thread adder = null

        when:
        holdingCatalogLock(env) {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder, env, lower)
            failing.armed.set(true)
            try {
                env.refresh()
            } catch (IllegalStateException ignored) {
                // expected
            }
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the refresh published nothing, and the add processed it"
        !adder.alive
        env.getProperty('lower.key', String).get() == 'lower'
        env.getProperty('shared', String).get() == 'lower'

        cleanup:
        env.stop()
    }

    void "a property source whose add waits while a refresh runs is processed once the add gets the lock, after a #after, as before"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        FailingSource failing = new FailingSource()
        RefreshesWhenRead refreshing = new RefreshesWhenRead(env)
        env.addPropertySource(failing)
        env.addPropertySource(refreshing)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['lower.someKey': 'lower'], -10)
        Thread adder = null

        when: "the add waits for the lock of the catalogs while a refresh runs, and then a stop"
        holdingCatalogLock(env) {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder, env, lower)
            env.refresh()
            env.stop()
            if (after == 'failed restart') {
                failing.armed.set(true)
                try {
                    env.start()
                } catch (IllegalStateException ignored) {
                    // expected
                }
            } else if (after == 'restart that a refresh overtakes') {
                refreshing.armed.set(true)
                env.start()
            }
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the add processed its property source, which no published catalog holds"
        !adder.alive
        env.getProperty('lower.some-key', String).get() == 'lower'

        cleanup:
        env.stop()

        where:
        after << ['stop', 'failed restart', 'restart that a refresh overtakes']
    }

    void "a property source whose add waits while a refresh runs is processed once, in its order, when a #after follows"() {
        given:
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        FailingSource failing = new FailingSource()
        env.addPropertySource(failing)
        env.addPropertySource(PropertySource.of('higher', ['shared': 'higher'], 0))
        env.start()
        PropertySource lower = PropertySource.of('lower', ['shared': 'lower', 'lower.key': 'lower'], -10)
        Thread adder = null

        when: "the add waits for the lock of the catalogs while a refresh runs, and then a restart or a refresh that fails"
        holdingCatalogLock(env) {
            adder = daemon('adder') { env.addPropertySource(lower) }
            awaitWaiting(adder, env, lower)
            env.refresh()
            if (after == 'restart') {
                env.stop()
                env.start()
            } else {
                failing.armed.set(true)
                try {
                    env.refresh()
                } catch (IllegalStateException ignored) {
                    // expected
                }
            }
        }
        adder.join(WAIT_SECONDS * 1000L)

        then: "the published catalog was read with it, in its order"
        !adder.alive
        env.getProperty('shared', String).get() == 'higher'
        env.getProperty('lower.key', String).get() == 'lower'

        cleanup:
        env.stop()

        where:
        after << ['restart', 'refresh that fails']
    }

    /**
     * The configuration of an environment that holds the property sources a test gives it and
     * nothing of the machine it runs on.
     */
    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() { ['test'] }

            @Override
            Optional<Boolean> getDeduceEnvironments() { Optional.of(false) }

            @Override
            boolean isEnableDefaultPropertySources() { false }
        }
    }

    /**
     * Runs the work while the current thread holds the lock of the catalogs, which the resolver
     * takes for the change it reports the difference of.
     */
    private static void holdingCatalogLock(DefaultEnvironment env, Closure<?> work) {
        ((PropertySourcePropertyResolver) env.delegate()).diff { work.call() }
    }

    /**
     * Waits until the adding thread has registered its property source and waits for the lock of
     * the catalogs: a thread can also be blocked for a moment before, while a class loads.
     */
    private static void awaitWaiting(Thread thread, DefaultEnvironment env, PropertySource added) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (thread.alive && !(thread.state == Thread.State.BLOCKED && env.propertySources.any { it.is(added) })) {
            assert System.nanoTime() < deadline: 'the adding thread never waited for the lock of the catalogs'
            Thread.sleep(5)
        }
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * A property source read first that, once armed, fails the read.
     */
    static class FailingSource implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()

        @Override
        String getName() { 'failing' }

        @Override
        int getOrder() { -100 }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                throw new IllegalStateException('failed to read')
            }
            return Collections.emptyIterator()
        }
    }

    /**
     * A property source read last that, once armed, refreshes the environment while it is read:
     * the refresh publishes other catalogs, and the read goes on into those.
     */
    static class RefreshesWhenRead implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        private final Environment env

        RefreshesWhenRead(Environment env) {
            this.env = env
        }

        @Override
        String getName() { 'refreshes' }

        @Override
        int getOrder() { Ordered.LOWEST_PRECEDENCE }

        @Override
        Object get(String key) { null }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                env.refresh()
            }
            return Collections.emptyIterator()
        }
    }
}
