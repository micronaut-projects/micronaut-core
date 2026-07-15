package io.micronaut.runtime.converters.time

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.core.convert.MutableConversionService
import io.micronaut.core.convert.exceptions.ConversionErrorException
import spock.lang.Specification

import java.time.ZoneId

class ZoneIdConverterSpec extends Specification {

    private ConversionService conversionService() {
        MutableConversionService conversionService = new DefaultMutableConversionService()
        new TimeConverterRegistrar().register(conversionService)
        conversionService
    }

    void "test invalid zone id '#value' converts to empty"() {
        given:
        ConversionService conversionService = conversionService()

        expect:
        !conversionService.convert(value, ZoneId).isPresent()

        where:
        value << ["foobar", "Europe/Foobar", "foo bar", "+25:00"]
    }

    void "test invalid zone id convert required throws conversion error"() {
        given:
        ConversionService conversionService = conversionService()

        when:
        conversionService.convertRequired("foobar", ZoneId)

        then:
        thrown(ConversionErrorException)
    }

    void "test valid zone id conversion"() {
        given:
        ConversionService conversionService = conversionService()

        expect:
        conversionService.convert("Europe/London", ZoneId).get() == ZoneId.of("Europe/London")
    }
}
