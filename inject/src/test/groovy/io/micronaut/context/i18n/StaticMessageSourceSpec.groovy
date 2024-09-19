package io.micronaut.context.i18n

import io.micronaut.context.MessageSource
import io.micronaut.context.StaticMessageSource
import spock.lang.Specification

class StaticMessageSourceSpec extends Specification {

    void "test static message source"() {
        given:
        def ms = new StaticMessageSource()
        ms.addMessage("foo.bar", "test")
        ms.addMessage(Locale.FRENCH, "foo.bar", "wi")

        expect:
        ms.getMessage("foo.bar", MessageSource.MessageContext.DEFAULT).get() == 'test'
        ms.getMessage("foo.bar", MessageSource.MessageContext.of(Locale.FRENCH)).get() == 'wi'
        ms.getMessage("foo.bar", MessageSource.MessageContext.DEFAULT).get() == 'test'
        ms.getMessage("foo.bar", MessageSource.MessageContext.of(Locale.FRENCH)).get() == 'wi'
    }

    void "test message interpolate"() {
        given:
        def ms = new StaticMessageSource()
        ms.addMessage("foo.bar", "test")
        ms.addMessage("foo.nested", "nested {foo.bar}")
        ms.addMessage(Locale.FRENCH, "foo.bar", "wi")

        expect:
        ms.interpolate("Say {foo.bar}.", MessageSource.MessageContext.DEFAULT) == 'Say test.'
        ms.interpolate("Say {foo.nested}.", MessageSource.MessageContext.DEFAULT) == 'Say nested test.'
        ms.interpolate("Say {foo.bar}.", MessageSource.MessageContext.of(Locale.FRENCH)) == 'Say wi.'
        ms.interpolate("Say {foo.bar}.", MessageSource.MessageContext.of(Locale.GERMAN)) == 'Say test.'
        ms.interpolate("Say {foo.bar}", MessageSource.MessageContext.DEFAULT) == 'Say test'
        ms.interpolate("Say {foo.bar}", MessageSource.MessageContext.of(Locale.FRENCH)) == 'Say wi'
        ms.interpolate("{foo.bar}", MessageSource.MessageContext.DEFAULT) == 'test'
        ms.interpolate("{foo.bar}", MessageSource.MessageContext.of(Locale.FRENCH)) == 'wi'
    }
}
