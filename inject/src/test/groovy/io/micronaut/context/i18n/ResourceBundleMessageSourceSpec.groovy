package io.micronaut.context.i18n

import io.micronaut.context.MessageSource
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

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

    void "test resource bundle message source resolves resources from context class loader"() {
        given:
        Path root = Files.createTempDirectory("message-source-context-class-loader")
        Path messages = root.resolve("i18n")
        Files.createDirectories(messages)
        Files.writeString(messages.resolve("messages.properties"), "hello.world=Hello World\n", StandardCharsets.UTF_8)
        Files.writeString(messages.resolve("messages_es.properties"), "hello.world=Hola Mundo\n", StandardCharsets.UTF_8)
        URLClassLoader contextClassLoader = new URLClassLoader([root.toUri().toURL()] as URL[], null)
        ClassLoader previous = Thread.currentThread().getContextClassLoader()
        Thread.currentThread().setContextClassLoader(contextClassLoader)

        when:
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource("i18n.messages")

        then:
        ms.getMessage("hello.world", MessageSource.MessageContext.DEFAULT).get() == 'Hello World'
        ms.getMessage("hello.world", MessageSource.MessageContext.of(new Locale("es"))).get() == 'Hola Mundo'

        cleanup:
        Thread.currentThread().setContextClassLoader(previous)
        contextClassLoader?.close()
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
