from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

MessageContext = java.type("io.micronaut.context.MessageSource$MessageContext")
MessageSource = java.type("io.micronaut.context.MessageSource")
Locale = java.type("java.util.Locale")


@Property(name="spec.name", value="I18nSpec")
@MicronautTest(startApplication=False)
class I18nSpec:
    messageSource: Annotated[MessageSource, Inject]

    @Test
    def itIsPossibleToCreateAMessageSourceFromResourceBundle(self) -> None:
        # tag::test[]
        assert self.messageSource.getMessage("hello", MessageContext.of(Locale("es"))).get() == "Hola"
        assert self.messageSource.getMessage("hello", MessageContext.of(Locale.ENGLISH)).get() == "Hello"
        # end::test[]

        assert self.messageSource.getMessage("hello", Locale("es")).isPresent()
        assert self.messageSource.getMessage("hello", Locale("es")).get() == "Hola"
        assert self.messageSource.getMessage("hello", Locale.ENGLISH).get() == "Hello"
        assert self.messageSource.getMessage("hello", Locale.ENGLISH).isPresent()
