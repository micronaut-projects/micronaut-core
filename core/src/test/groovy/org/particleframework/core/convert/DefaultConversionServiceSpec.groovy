package org.particleframework.core.convert

import org.particleframework.core.convert.format.ReadableBytes
import spock.lang.Specification

import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.time.DayOfWeek

/**
 * Created by graemerocher on 12/06/2017.
 */
class DefaultConversionServiceSpec extends Specification {

    void "test default conversion service"() {
        given:
        ConversionService conversionService = new DefaultConversionService()

        expect:
        conversionService.convert(sourceObject, targetType).get() == result

        where:
        sourceObject      | targetType | result
        10                | Long       | 10L
        10                | Float      | 10.0f
        10                | String     | "10"
        "1,2"             | int[]      | [1, 2] as int[]
        "10"              | Integer    | 10
        "${5 + 5}"        | Integer    | 10
        "yes"             | Boolean    | true
        "Y"               | Boolean    | true
        "yes"             | boolean    | true
        "on"              | boolean    | true
        "off"             | boolean    | false
        "false"           | boolean    | false
        "n"               | boolean    | false
        "USD"             | Currency   | Currency.getInstance("USD")
        "CET"             | TimeZone   | TimeZone.getTimeZone("CET")
        "http://test.com" | URL        | new URL("http://test.com")
        "http://test.com" | URI        | new URI("http://test.com")
        "monday"          | DayOfWeek  | DayOfWeek.MONDAY

    }

    void "test conversion service with type arguments"() {
        given:
        ConversionService conversionService = new DefaultConversionService()

        expect:
        conversionService.convert(sourceObject, targetType, ConversionContext.of(typeArguments)).get() == result

        where:
        sourceObject | targetType | typeArguments | result
        "1,2"        | List       | [E: Integer]  | [1, 2]
        "1,2"        | Iterable   | [T: Long]     | [1l, 2l]
        "1"          | Optional   | [T: Long]     | Optional.of(1L)

    }

    @Format('yyyy/mm/dd')
    private Date today

    void "test conversion service with formatting and date"() {
        given:
        ConversionService conversionService = new DefaultConversionService()
        Field field = getClass().getDeclaredField("today")
        expect:
        conversionService.convert(sourceObject, targetType, ConversionContext.of(field, Locale.ENGLISH)).get() == result

        where:
        sourceObject | targetType | result
        "1999/01/01" | Date       | Date.parse("yyyy/mm/dd", "1999/01/01")
    }


    @ReadableBytes
    private int maxSize

    void "test conversion service with formatting for bytes"() {
        given:
        ConversionService conversionService = new DefaultConversionService()
        Field field = getClass().getDeclaredField("maxSize")
        expect:
        conversionService.convert(sourceObject, targetType, ConversionContext.of(field, Locale.ENGLISH)).get() == result

        where:
        sourceObject | targetType | result
        "1MB"        | Integer    | 1048576
    }
}
