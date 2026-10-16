package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.annotation.BootstrapContextCompatible
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

        and: "it stays removed"
        !env.getPropertySources()*.name.contains('late')

        cleanup:
        gate.release.countDown()
        env.stop()
    }

    void "a property source that the environment adds while it reads, before it collects the property sources, keeps its order"() {
        given: "an environment that adds a property source of a low order whenever it reads, as the bootstrap of a distributed configuration does"
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources()) {
            @Override
            protected void readPropertySources(String name) {
                addPropertySource(PropertySource.of('remote', ['app.value': 'remote', 'remote.key': 'remote'], 10))
                super.readPropertySources(name)
            }
        }
        env.addPropertySource(PropertySource.of('local', ['app.value': 'local'], 20))

        when:
        env.start()

        then: "it is processed once, in its order, so the property source of the higher order still overrides it"
        env.getProperty('remote.key', String).get() == 'remote'
        env.getProperty('app.value', String).get() == 'local'

        when:
        env.refresh()

        then:
        env.getProperty('remote.key', String).get() == 'remote'
        env.getProperty('app.value', String).get() == 'local'

        cleanup:
        env.stop()
    }

    void "the property sources of the bootstrap, which the environment adds while it reads, keep their order"() {
        when: "a distributed configuration of a lower order than the application configuration is found while the environment reads"
        ApplicationContext ctx = ApplicationContext.builder()
                .properties(['spec.name': 'AddPropertySourceDuringReadSpec'])
                .bootstrapEnvironment(true)
                .start()

        then: "it is processed once, in its order, so the application configuration still overrides it"
        ctx.getProperty('add-during-read.remote', String).get() == 'remote'
        ctx.getProperty('config.prop', String).get() == 'application.yml'

        when:
        ctx.environment.refresh()

        then:
        ctx.getProperty('add-during-read.remote', String).get() == 'remote'
        ctx.getProperty('config.prop', String).get() == 'application.yml'

        cleanup:
        ctx?.close()
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
     * A distributed configuration, found while the environment reads its property sources, of a
     * lower order than the application configuration, which sets {@code config.prop} too.
     */
    @BootstrapContextCompatible
    @Singleton
    @Requires(property = 'spec.name', value = 'AddPropertySourceDuringReadSpec')
    static class RemoteLocator implements BootstrapPropertySourceLocator {
        @Override
        Iterable<PropertySource> findPropertySources(Environment environment) throws ConfigurationException {
            [PropertySource.of('remote',
                    ['config.prop': 'remote', 'add-during-read.remote': 'remote'],
                    AbstractPropertySourceLoader.DEFAULT_POSITION - 100)]
        }
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
            }
            return Collections.emptyIterator()
        }
    }
}
