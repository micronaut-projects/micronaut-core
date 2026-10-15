package io.micronaut.core.convert.value

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import spock.lang.Specification

class ConvertibleValuesMapGetSpec extends Specification {

    void "get by class returns the same instance when the value already has the required type"() {
        given:
        def value = new StringBuilder("abc")
        def values = new MutableConvertibleValuesMap<Object>([exact: value, str: 'text'])

        expect:
        values.get('exact', StringBuilder).get().is(value)
        values.get('exact', CharSequence).get().is(value)
        values.get('exact', Object).get().is(value)
        values.get('str', String).get() == 'text'
    }

    void "get by class boxes primitive required types"() {
        given:
        def values = new MutableConvertibleValuesMap<Object>([int: 10, bool: true, str: '42'])

        expect:
        values.get('int', int.class).get() == 10
        values.get('int', Integer).get() == 10
        values.get('bool', boolean.class).get() == true
        values.get('str', int.class).get() == 42
        values.get('int', long.class).get() == 10L
    }

    void "get by class returns empty for missing values"() {
        given:
        def values = new MutableConvertibleValuesMap<Object>([:])

        expect:
        !values.get('missing', String).isPresent()
        !values.get('missing', int.class).isPresent()
        !values.get('missing', Object).isPresent()
    }

    void "get by class converts values of a different type"() {
        given:
        def values = new MutableConvertibleValuesMap<Object>([num: '123', int: 5, bad: 'abc'])

        expect:
        values.get('num', Integer).get() == 123
        values.get('num', Long).get() == 123L
        values.get('int', String).get() == '5'
        !values.get('bad', Integer).isPresent()
    }

    void "get by class matches the conversion context path"() {
        given:
        def values = new MutableConvertibleValuesMap<Object>([
                str : 'text',
                num : '7',
                int : 3,
                list: ['1', '2'],
                map : [a: 'b'],
                sb  : new StringBuilder('x')
        ])

        expect:
        values.get(name, type) == values.get(name, ConversionContext.of(Argument.of(type)))

        where:
        name   | type
        'str'  | String
        'str'  | CharSequence
        'str'  | Integer
        'num'  | int.class
        'num'  | Integer
        'int'  | int.class
        'int'  | String
        'list' | List
        'list' | Collection
        'list' | Object
        'map'  | Map
        'map'  | Object
        'sb'   | CharSequence
        'sb'   | String
        'none' | String
    }

    void "get by class uses the configured conversion service for conversions"() {
        given:
        def conversionService = Mock(ConversionService)
        def values = new MutableConvertibleValuesMap<Object>([str: 'text', num: '1'], conversionService)

        when:
        def exact = values.get('str', String)

        then:
        exact.get() == 'text'
        0 * conversionService._

        when:
        def converted = values.get('num', Integer)

        then:
        1 * conversionService.convert('1', { it.argument.type == Integer }) >> Optional.of(99)
        converted.get() == 99
    }

    void "conversion errors are reported through the conversion context"() {
        given:
        def values = new MutableConvertibleValuesMap<Object>([bad: 'abc'])
        def context = ConversionContext.of(Argument.of(Integer))

        expect:
        !context.lastError.isPresent()
        !context.iterator().hasNext()

        when:
        def result = values.get('bad', context)

        then:
        !result.isPresent()
        context.lastError.isPresent()
        context.lastError.get().cause instanceof NumberFormatException
        context.lastError.get().originalValue.get() == 'abc'
        context.iterator().toList().size() == 1
    }

    void "conversion context keeps all rejections in order"() {
        given:
        def context = ConversionContext.of(Argument.of(Integer))
        def first = new IllegalStateException('first')
        def second = new IllegalArgumentException('second')

        when:
        context.reject(null)
        context.reject('value', null)

        then:
        !context.lastError.isPresent()
        !context.iterator().hasNext()

        when:
        context.reject(first)
        context.reject('value', second)

        then:
        context.iterator().toList()*.cause == [first, second]
        context.lastError.get().cause.is(second)
        context.lastError.get().originalValue.get() == 'value'

        when:
        context.iterator().remove()

        then:
        thrown(UnsupportedOperationException)
    }
}
