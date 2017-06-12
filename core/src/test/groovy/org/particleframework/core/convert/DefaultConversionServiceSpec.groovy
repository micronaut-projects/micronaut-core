package org.particleframework.core.convert

import spock.lang.Specification

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
}
