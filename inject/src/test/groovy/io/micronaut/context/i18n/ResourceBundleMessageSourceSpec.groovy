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
}
