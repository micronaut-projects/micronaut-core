package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextConfiguration
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A lookup resolves the placeholders of a value in the properties it read the value from, once,
 * even when a refresh is published while it runs. On 5.2.x such a lookup was run a second time,
 * so the converters it called ran twice.
 */
@Timeout(60)
class OvertakenLookupConvertsOnceSpec extends Specification {

    private static final int WAIT_SECONDS = 10

    void "a lookup that a refresh overtakes converts the property its placeholder names once"() {
        given: "a value whose text can be parked, with a placeholder among other text, so the property it names is converted to a String"
        AtomicInteger conversions = new AtomicInteger()
        ParkedText text = new ParkedText('value-${target}')
        Map<String, Object> data = ['app.foo': text, 'target': new Token('old')]
        DefaultEnvironment env = new DefaultEnvironment(withoutDefaultPropertySources())
        env.addPropertySource(new MapPropertySource('data', data))
        env.start()
        env.conversionService.addConverter(Token, String, { Token token ->
            conversions.incrementAndGet()
            token.value
        })
        ExecutorService executor = Executors.newSingleThreadExecutor()

        when: "a refresh changes both properties while the lookup is parked between reading the value and resolving its placeholder"
        text.arm()
        Future<Object> reader = executor.submit({ env.getProperty('app.foo', String).get() } as Callable<Object>)
        assert text.reading.await(WAIT_SECONDS, TimeUnit.SECONDS)
        data.put('app.foo', 'value-${target}')
        data.put('target', new Token('new'))
        env.refresh()
        text.release.countDown()

        then: "the lookup resolves the placeholder from the properties it read the value from"
        reader.get(WAIT_SECONDS, TimeUnit.SECONDS) == 'value-old'

        and: "it converted the property once: 5.2.x ran the lookup again, and converted it twice"
        conversions.get() == 1

        cleanup:
        text.release.countDown()
        executor.shutdownNow()
        env.stop()
    }

    private static ApplicationContextConfiguration withoutDefaultPropertySources() {
        ApplicationContext.builder()
                .environments('test')
                .deduceEnvironment(false)
                .enableDefaultPropertySources(false) as ApplicationContextConfiguration
    }

    static class Token {
        final String value

        Token(String value) {
            this.value = value
        }

        @Override
        String toString() { "Token($value)" }
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
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException('the text was never released')
                }
            }
            return text
        }
    }
}
