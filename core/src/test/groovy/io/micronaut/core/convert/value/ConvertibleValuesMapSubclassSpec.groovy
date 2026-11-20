package io.micronaut.core.convert.value

import io.micronaut.core.convert.ArgumentConversionContext
import spock.lang.Specification

class ConvertibleValuesMapSubclassSpec extends Specification {

    void "get with a class goes through the conversion context get of a subclass"() {
        given:
        def values = new ConvertibleValuesMap<Object>([name: 'value']) {
            @Override
            <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
                return Optional.of((T) 'overridden')
            }
        }

        expect:
        values.get('name', String).get() == 'overridden'
        values.get('name', Object).get() == 'overridden'
    }

    void "get with a class keeps the fast path of the map itself"() {
        given:
        def values = new ConvertibleValuesMap<Object>([name: 'value', number: 1])

        expect:
        values.get('name', String).get() == 'value'
        values.get('number', int).get() == 1
        values.get('number', String).get() == '1'
    }
}
