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
 * A start or a refresh that a stop overtakes while it reads the property sources, with a property
 * source added or the environment started again meanwhile. The stop returns at once and empties the
 * environment; the overtaken read publishes and registers nothing, not even an add handed over to
 * it, and a start after the stop reads at once. The read is parked inside a property source, and
 * every wait is bounded.
 */
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/14141')
@Timeout(60)
class StoppedReadSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "an environment stopped while a #read is parked is empty once the stop returns, and the read publishes nothing after it, also when #change"() {
        given: "a property source that, once armed, parks the read where it reads it, before the others"
        ParkingPropertySource first = new ParkingPropertySource('first', ['first.key': 'f'])
        PropertySource data = PropertySource.of('data', ['app.value': 'a'], 10)
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(first)
        env.addPropertySource(data)
        if (read == 'refresh') {
            env.start()
        }
        Throwable failure = null
        String addedDuringRead = null
        String startedAgain = null

        when: "it is stopped while the read is parked"
        Thread reader = parkIn(first) {
            try {
                read == 'refresh' ? env.refresh() : env.start()
            } catch (Throwable t) {
                failure = t
            }
        }
        if (change == 'a property source is added meanwhile') {
            changeWhileParked {
                env.addPropertySource(PropertySource.of('q', ['q.key': 'q']))
            }
            addedDuringRead = env.getProperty('q.key', String).orElse(null)
        }
        changeWhileParked {
            env.stop()
        }
        boolean emptyAfterStop = !env.running && !env.containsProperty('app.value') && !env.containsProperty('first.key') && !env.containsProperty('q.key')
        if (change == 'it is started again meanwhile') {
            env.removePropertySource(data)
            changeWhileParked {
                env.start()
            }
            startedAgain = env.getProperty('first.key', String).orElse(null)
        }
        boolean parkedMeanwhile = reader.alive
        release(first, reader)

        then: "the stop returned at once and left the environment empty; an add made before it was visible at once"
        emptyAfterStop
        addedDuringRead == qKey

        and: "a start after the stop does not wait for the parked read, and reads at once"
        parkedMeanwhile
        startedAgain == firstKey

        and: "the parked read publishes nothing once released, not even what was handed over to it"
        failure == null
        !env.getProperty('app.value', String).present
        env.getProperty('first.key', String).orElse(null) == firstKey
        !env.getProperty('q.key', String).present
        env.running == (change == 'it is started again meanwhile')

        and: "what was added stays registered"
        env.propertySources*.name.contains('q') == (qKey != null)

        when: "it is stopped, and started again without the property source 'data'"
        env.stop()
        env.removePropertySource(data)
        env.start()

        then: "it reads what is registered, an add made during the stopped read included"
        !env.getProperty('app.value', String).present
        env.getProperty('first.key', String).get() == 'f'
        env.getProperty('q.key', String).orElse(null) == qKey

        cleanup:
        first.release.countDown()
        env.stop()

        where:
        [read, change] << [['start', 'refresh'], ['a property source is added meanwhile', 'it is started again meanwhile']].combinations()
        firstKey = change == 'it is started again meanwhile' ? 'f' : null
        qKey = change == 'a property source is added meanwhile' ? 'q' : null
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
     * Runs the read on another thread, once the parking is armed, and returns that thread once the
     * read is parked.
     */
    private static Thread parkIn(ParkingPropertySource parking, Closure<?> read) {
        parking.arm()
        Thread reader = daemon('reader', read)
        assert parking.reached.await(WAIT_SECONDS, TimeUnit.SECONDS)
        return reader
    }

    /**
     * Runs the change on another thread, which must finish while the read is parked.
     */
    private static void changeWhileParked(Closure<?> change) {
        Thread changer = daemon('changer', change)
        changer.join(WAIT_SECONDS * 1000L)
        assert !changer.alive
    }

    private static void release(ParkingPropertySource parking, Thread reader) {
        parking.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)
        assert !reader.alive
    }

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }

    /**
     * A property source with the given properties that parks the thread that iterates it, once
     * armed.
     */
    static class ParkingPropertySource implements PropertySource {
        final CountDownLatch reached = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final AtomicBoolean armed = new AtomicBoolean()
        private final String name
        private final Map<String, Object> values

        ParkingPropertySource(String name, Map<String, Object> values) {
            this.name = name
            this.values = values
        }

        void arm() {
            armed.set(true)
        }

        @Override
        String getName() { name }

        @Override
        Object get(String key) { values.get(key) }

        @Override
        Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                reached.countDown()
                // qualified: unqualified, Groovy resolves the name through get(String) of this property source
                if (!release.await(StoppedReadSpec.WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the parked read was never released')
                }
            }
            return values.keySet().iterator()
        }
    }
}
