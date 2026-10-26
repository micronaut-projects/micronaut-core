package io.micronaut.context.env

import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What a {@code ${random.*}} expression resolved to is remembered for the text found at a place,
 * and only a lookup of a state at least as new replaces it with another text. A state that finds
 * the remembered text again makes it its own.
 */
@Timeout(60)
class RememberedValuesOrderSpec extends Specification {

    static final int WAIT_SECONDS = 10

    void "a random value stays when a lookup of a text that a newer state replaced again ends after a lookup of that newer state"() {
        given: "a property whose text holds a random value, looked up once"
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver()
        resolver.addPropertySource(PropertySource.of('a', ['k': 'A-${random.uuid}']))
        String first = resolver.getProperty('k', String).get()
        ParkedText replacement = new ParkedText('B-${random.uuid}')
        resolver.addPropertySource(PropertySource.of('b', ['k': replacement]))

        when: "a lookup of the replacement text is parked, the first text is put back and looked up, and the parked lookup ends"
        replacement.arm()
        Thread reader = new Thread({ resolver.getProperty('k', String).get() }, 'reader')
        reader.daemon = true
        reader.start()
        assert replacement.reading.await(WAIT_SECONDS, TimeUnit.SECONDS)
        resolver.addPropertySource(PropertySource.of('a-again', ['k': 'A-${random.uuid}']))
        String again = resolver.getProperty('k', String).get()
        replacement.release.countDown()
        reader.join(WAIT_SECONDS * 1000L)

        and: "an unrelated property source is added, which empties the caches"
        resolver.addPropertySource(PropertySource.of('unrelated', ['other': 'x']))

        then: "the first text keeps its random value"
        !reader.alive
        again == first
        resolver.getProperty('k', String).get() == first

        cleanup:
        replacement.release.countDown()
    }

    /**
     * A property value that, once armed, parks the thread that reads its text.
     */
    static class ParkedText implements CharSequence {
        final CountDownLatch reading = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)
        private final String text
        private final AtomicBoolean armed = new AtomicBoolean()

        ParkedText(String text) {
            this.text = text
        }

        void arm() {
            armed.set(true)
        }

        @Override
        int length() { text.length() }

        @Override
        char charAt(int index) { text.charAt(index) }

        @Override
        CharSequence subSequence(int start, int end) { text.subSequence(start, end) }

        @Override
        String toString() {
            if (armed.compareAndSet(true, false)) {
                reading.countDown()
                release.await(RememberedValuesOrderSpec.WAIT_SECONDS, TimeUnit.SECONDS)
            }
            return text
        }
    }
}
