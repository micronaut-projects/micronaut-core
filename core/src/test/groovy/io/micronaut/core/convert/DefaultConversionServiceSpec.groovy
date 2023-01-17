package io.micronaut.core.convert

import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
/**
 * Created by graemerocher on 12/06/2017.
 */
class DefaultConversionServiceSpec extends Specification {

    void "test default conversion service converts a #sourceObject.class.name to a #targetType.name"() {
        given:
        ConversionService conversionService = new DefaultMutableConversionService()

        expect:
        conversionService.convert(sourceObject, targetType).get() == result

        where:
        sourceObject                            | targetType  | result
        10                                      | Long        | 10L
        10                                      | Float       | 10.0f
        10                                      | String      | "10"
        "1,2"                                   | int[]       | [1, 2] as int[]
        "str"                                   | char[]      | ['s', 't', 'r'] as char[]
        "10"                                    | Byte        | 10
        "10"                                    | Integer     | 10
        "${5 + 5}"                              | Integer     | 10
        "10"                                    | BigInteger  | BigInteger.valueOf(10)
        "yes"                                   | Boolean     | true
        "true"                                  | Boolean     | true
        "Y"                                     | Boolean     | true
        "yes"                                   | boolean     | true
        "on"                                    | boolean     | true
        "off"                                   | boolean     | false
        "false"                                 | boolean     | false
        "n"                                     | boolean     | false
        Boolean.TRUE                            | boolean     | true
        "USD"                                   | Currency    | Currency.getInstance("USD")
        "CET"                                   | TimeZone    | TimeZone.getTimeZone("CET")
        "http://test.com"                       | URL         | new URL("http://test.com")
        "http://test.com"                       | URI         | new URI("http://test.com")
        "monday"                                | DayOfWeek   | DayOfWeek.MONDAY
        ["monday"] as String[]                  | DayOfWeek   | DayOfWeek.MONDAY
        ["monday"] as String[]                  | DayOfWeek[] | [DayOfWeek.MONDAY] as DayOfWeek[]
        "monday,tuesday,monday"                 | Set         | ["monday", "tuesday"] as Set
        "N/A"                                   | Status      | Status.N_OR_A
        ["OK", "N/A"]                           | Status[]    | [Status.OK, Status.N_OR_A]
        "test".getBytes(StandardCharsets.UTF_8) | String      | "test"
    }

    void 'test properties conversion'() {
        given:
        ConversionService conversionService = new DefaultMutableConversionService()

        when:
        def sourceObject = [num: 1, name: 'tim', cool: true]
        def targetType = Properties

        then:
        with(conversionService.convert(sourceObject, targetType).get()) {
            it.size() == 3
            it.num == 1
            it.name == 'tim'
            it.cool == true
        }

        when:
        sourceObject = [1: 1, name: 'tim', cool: true]

        then:
        with(conversionService.convert(sourceObject, targetType).get()) {
            it.size() == 3
            it.containsKey(1)
            it.get(1) == 1
            it.name == 'tim'
            it.cool == true
        }
    }

    void "test empty string conversion"() {
        given:
        ConversionService conversionService = new DefaultMutableConversionService()

        expect:
        !conversionService.convert("", targetType).isPresent()

        where:
        targetType << [File, Date, Integer, BigInteger, Float, Double, Long, Short, Byte, BigDecimal, URL, URI, Locale, UUID, Currency, TimeZone, Charset, Status]
    }

    void "test convert required"() {
        given:
        ConversionService conversionService = new DefaultMutableConversionService()

        when:
        conversionService.convertRequired("junk", Integer)

        then:
        def e = thrown(ConversionErrorException)
        e.conversionError.originalValue.get() == 'junk'
        e.message == 'Failed to convert argument [integer] for value [junk] due to: For input string: "junk"'
    }

    void "test conversion service with type arguments"() {
        given:
        ConversionService conversionService = new DefaultMutableConversionService()

        expect:
        conversionService.convert(sourceObject, targetType, ConversionContext.of(typeArguments)).get() == result

        where:
        sourceObject | targetType | typeArguments                  | result
        "1,2"        | List       | [E: Argument.of(Integer, 'E')] | [1, 2]
        "1,2"        | Iterable   | [T: Argument.of(Long, 'T')]    | [1l, 2l]
        "1"          | Optional   | [T: Argument.of(Long, 'T')]    | Optional.of(1L)

    }
}
