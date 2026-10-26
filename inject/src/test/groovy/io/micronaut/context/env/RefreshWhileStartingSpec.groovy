package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * {@code Environment#refresh()} from another thread while the environment starts. The start is
 * parked between two property sources while the refresh is called, and every wait is bounded.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14139')
@Timeout(60)
class RefreshWhileStartingSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a refresh during a start publishes no empty properties, and no property disappears once seen"() {
        given: "a property source processed after 'data' that parks the start when it is asked for its convention"
        Later later = new Later()
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(PropertySource.of('data', ['app.value': 'a'], 0))
        env.addPropertySource(later)
        Thread starter = daemon('starter') { env.start() }
        Thread refresher = daemon('refresher') { env.refresh() }
        List<String> seen = Collections.synchronizedList([])

        when: "another thread refreshes the environment once the start has published 'data'"
        later.armed.set(true)
        starter.start()
        assert later.parked.await(WAIT_SECONDS, TimeUnit.SECONDS)
        seen << env.getProperty('app.value', String).orElse(null)
        refresher.start()
        new PollingConditions(timeout: WAIT_SECONDS).eventually {
            assert !refresher.alive || refresher.state in [Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING]
        }
        seen << env.getProperty('app.value', String).orElse(null)
        later.release.countDown()
        starter.join(WAIT_SECONDS * 1000L)
        seen << env.getProperty('app.value', String).orElse(null)
        refresher.join(WAIT_SECONDS * 1000L)
        seen << env.getProperty('app.value', String).orElse(null)

        then: "every lookup saw the value"
        !starter.alive
        !refresher.alive
        seen == ['a', 'a', 'a', 'a']

        and: "the start and the refresh have read every property source"
        env.getProperty('later.key', String).get() == 'later'

        cleanup:
        later.release.countDown()
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        return thread
    }

    /**
     * A property source processed after 'data' that, once armed, parks the thread that asks for its
     * convention, which a refresh does before it processes it.
     */
    static class Later implements PropertySource {
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
        PropertyConvention getConvention() {
            if (armed.compareAndSet(true, false)) {
                parked.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(RefreshWhileStartingSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the start was never released')
                }
            }
            return PropertyConvention.JAVA_PROPERTIES
        }
    }
}
