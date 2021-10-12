package io.micronaut.context.i18n

import io.micronaut.context.MessageSource
import spock.lang.Specification

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
}
