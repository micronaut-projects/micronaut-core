package io.micronaut.context.i18n

import io.micronaut.context.MessageSource
import spock.lang.Specification

import java.lang.reflect.Field

class ResourceBundleMessageSourceSpec extends Specification {

    void "test resource bundle message source"() {
        given:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.context.i18n.Test")

        expect:
        ms.getMessage("hello.message", MessageSource.MessageContext.DEFAULT).get() == 'Hello'
        ms.getMessage("hello.message", MessageSource.MessageContext.of(new Locale("es"))).get() == 'Hola'
        // repeated to exercise cache
        ms.getMessage("hello.message", MessageSource.MessageContext.DEFAULT).get() == 'Hello'
        ms.getMessage("hello.message", MessageSource.MessageContext.of(new Locale("es"))).get() == 'Hola'
    }

    void "test default locale"() {
        given:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.context.i18n.Test", new Locale("xx"))

        expect:
        ms.getMessage("hello.message", MessageSource.MessageContext.DEFAULT).get() == 'Hello XX'
        ms.getMessage("hello.message", MessageSource.MessageContext.of([:])).get() == 'Hello XX'
        ms.getMessage("hello.message", MessageSource.MessageContext.of(Locale.ENGLISH)).get() == 'Hello'
    }

    void "test message interpolation escaping"() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.context.i18n.Test")

        when:
        String result = ms.interpolate(template, MessageSource.MessageContext.of(['0': 'A', '1': 'B', '2': 'C', '{{x': 'D']))

        then:
        result == expected

        where:
        template                 | expected
        "test {0} {1} {2}"       | "test A B C"
        "test {0} {1} {2}'"      | "test A B C"
        "test {0} {1} {2}{"      | "test A B C{"
        "test {0} {1} {2}}"      | "test A B C}"
        "test {0} '{1}' {2}"     | "test A {1} C"
        "test {0} ''{1}'' {2}"   | "test A 'B' C"
        "test {0} '''{1}''' {2}" | "test A '{1}' C"
        "test {0} '{1} {2}"      | "test A {1} {2}"
        "test {0} ''{1} {2}"     | "test A 'B C"
        "test {{{x}}}"           | "test D}}"
        "test {abcd"             | "test {abcd"
    }

    void "successful message lookups are cached"() {
        given:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.context.i18n.Test")

        when: "a message that resolves successfully is looked up"
        Optional<String> result = ms.getRawMessage("hello.message", MessageSource.MessageContext.of(Locale.ENGLISH))

        then: "it resolves and the successful resolution is stored in the message cache"
        result.get() == 'Hello'
        messageCache(ms).size() == 1
        messageCache(ms).values().first().get() == 'Hello'
    }

    void "missing bundle lookups are cached"() {
        given:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("io.micronaut.context.i18n.DoesNotExist")

        when: "a code is looked up but no bundle can be resolved"
        Optional<String> result = ms.getRawMessage("any.code", MessageSource.MessageContext.of(Locale.ENGLISH))

        then: "the empty result is cached too"
        !result.present
        messageCache(ms).size() == 1
        !messageCache(ms).values().first().present
    }

    private static Map<?, Optional<String>> messageCache(ResourceBundleMessageSource ms) {
        // The cache is private; read it via reflection, mirroring test-suite's
        // ResourceBundleMessageSourceTest which reflects into bundleCache.
        Field field = ResourceBundleMessageSource.getDeclaredField("messageCache")
        field.setAccessible(true)
        return (Map<?, Optional<String>>) field.get(ms)
    }
}
