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
 * A refresh from another thread while the environment starts, and a refresh that a property
 * source runs while the start reads it, keep the properties the start has read.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14139')
@Timeout(60)
class RefreshDuringStartSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a refresh from another thread while the environment starts keeps the properties the start has read"() {
        given: "a start parked after it has read data, before later"
        Later later = new Later()
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.value': 'a'], 0), later)
        later.armed.set(true)
        Thread starter = new Thread({ env.start() }, 'starter')
        starter.daemon = true
        starter.start()
        assert later.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when: "another thread refreshes while the start is parked"
        Thread refresher = new Thread({ env.refresh() }, 'refresher')
        refresher.daemon = true
        refresher.start()
        refresher.join(2000)

        then: "the refresh waits for the start rather than publishing the empty catalog it did not fill"
        refresher.alive

        when: "the start finishes"
        later.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the start published what it read, and the refresh published what it read after it"
        !starter.alive
        !refresher.alive
        env.getProperty('app.value', String).orElse(null) == 'a'
        env.getProperty('later.key', String).orElse(null) == 'later'

        cleanup:
        later.release.countDown()
        env.stop()
    }

    void "a refresh from another thread while the environment starts leaves the difference of the refresh alone"() {
        given: "a start parked after it has read data, before later"
        Later later = new Later()
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.value': 'a'], 0), later)
        later.armed.set(true)
        Thread starter = new Thread({ env.start() }, 'starter')
        starter.daemon = true
        starter.start()
        assert later.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)

        when: "another thread takes the difference of a refresh while the start is parked"
        Map<String, Object> diff = null
        Thread refresher = new Thread({ diff = env.refreshAndDiff() }, 'refresher')
        refresher.daemon = true
        refresher.start()
        // the refresh waits for the start once it has tried: it parks while the start is parked,
        // and only returns once the start has finished
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (refresher.alive && refresher.state != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
            Thread.sleep(5)
        }
        assert refresher.alive && refresher.state == Thread.State.TIMED_WAITING
        later.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)
        refresher.join(WAIT_SECONDS * 1000L)

        then: "the refresh read what the start had read already, so it changed nothing"
        !starter.alive
        !refresher.alive
        diff != null
        diff.isEmpty()
        env.getProperty('app.value', String).orElse(null) == 'a'
        env.getProperty('later.key', String).orElse(null) == 'later'

        cleanup:
        later.release.countDown()
        env.stop()
    }

    void "a property source that refreshes the environment while the start reads it keeps the properties the start has read"() {
        given: "a property source read after data that refreshes the environment while it is read"
        AtomicBoolean ran = new AtomicBoolean()
        DefaultEnvironment env = environmentWith(PropertySource.of('data', ['app.first': 'one'], 1))
        PropertySource refreshing = new MapPropertySource('refreshing', ['app.second': 'two', 'app.trigger': 'x', 'app.third': 'three']) {
            @Override
            Object get(String key) {
                if (key == 'app.trigger' && ran.compareAndSet(false, true)) {
                    env.refresh()
                }
                return super.get(key)
            }

            @Override
            int getOrder() { 2 }
        }
        env.addPropertySource(refreshing)
        env.addPropertySource(PropertySource.of('later', ['app.fourth': 'four'], 3))

        when:
        env.start()

        then: "the refresh published what it read, and the start went on into those"
        env.getProperty('app', Map).get() == [first: 'one', second: 'two', trigger: 'x', third: 'three', fourth: 'four']

        cleanup:
        env.stop()
    }

    private static DefaultEnvironment environmentWith(PropertySource... propertySources) {
        DefaultEnvironment env = new DefaultEnvironment(ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration)
        for (PropertySource propertySource : propertySources) {
            env.addPropertySource(propertySource)
        }
        return env
    }

    /**
     * A property source processed after {@code data} that, once armed, parks the thread that asks
     * for its convention. A start asks for it before it takes the lock of the catalogs to process
     * the property source.
     */
    static final class Later implements PropertySource {
        final AtomicBoolean armed = new AtomicBoolean()
        final CountDownLatch parked = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Override
        String getName() { 'later' }

        @Override
        int getOrder() { 10 }

        @Override
        Object get(String key) { key == 'later.key' ? 'later' : null }

        @Override
        Iterator<String> iterator() { ['later.key'].iterator() }

        @Override
        PropertySource.PropertyConvention getConvention() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown()
                release.await(RefreshDuringStartSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return PropertySource.PropertyConvention.JAVA_PROPERTIES
        }
    }
}
