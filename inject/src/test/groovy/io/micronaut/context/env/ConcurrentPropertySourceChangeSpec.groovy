package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Refreshes while another thread adds and removes a property source, as a configuration watch
 * does. Every wait is bounded.
 */
@Timeout(60)
class ConcurrentPropertySourceChangeSpec extends Specification {

    private static final int WAIT_SECONDS = 10

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

    private static Thread daemon(String name, Closure<?> work) {
        Thread thread = new Thread(work, name)
        thread.daemon = true
        thread.start()
        return thread
    }
}
