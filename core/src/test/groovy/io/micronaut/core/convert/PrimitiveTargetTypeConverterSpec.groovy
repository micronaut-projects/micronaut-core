package io.micronaut.core.convert

import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.core.type.Argument
import spock.lang.Specification

class PrimitiveTargetTypeConverterSpec extends Specification {

    void "a resolved converter of a built-in parser converts to primitives"() {
        given:
        def service = new DefaultMutableConversionService()

        expect:
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.INT)).getInt("12345", ConversionContext.DEFAULT) == 12345
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.INT)).findInt("-7", ConversionContext.DEFAULT) == OptionalInt.of(-7)
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.LONG)).getLong("9000000000", ConversionContext.DEFAULT) == 9000000000L
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.LONG)).findLong("1", ConversionContext.DEFAULT) == OptionalLong.of(1)
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.DOUBLE)).getDouble("1.5", ConversionContext.DEFAULT) == 1.5d
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.DOUBLE)).findDouble("2", ConversionContext.DEFAULT) == OptionalDouble.of(2d)
        PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.BOOLEAN)).getBoolean("Yes", ConversionContext.DEFAULT)
        !PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.BOOLEAN)).getBoolean("nope", ConversionContext.DEFAULT)
    }

    void "a resolved converter is a primitive converter itself"() {
        given:
        def converter = new DefaultMutableConversionService().resolveConverter(String, Argument.INT)

        expect:
        converter instanceof PrimitiveTargetTypeConverter
        PrimitiveTargetTypeConverter.of(converter).is(converter)
    }

    void "a value that does not parse is rejected once and fails like the conversion"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.INT))

        when:
        def context = ConversionContext.of(Argument.INT)
        def found = converter.findInt("x", context)

        then:
        found.isEmpty()
        context.toList().size() == 1
        context.getLastError().get().cause instanceof NumberFormatException

        when:
        context = ConversionContext.of(Argument.INT)
        converter.getInt("x", context)

        then:
        def e = thrown(ConversionErrorException)
        e.conversionError.cause instanceof NumberFormatException
        context.toList().size() == 1

        when: "an empty value converts to nothing, without an error"
        context = ConversionContext.of(Argument.INT)
        def empty = converter.findInt("", context)

        then:
        empty.isEmpty()
        context.toList().isEmpty()

        when:
        converter.getInt("", ConversionContext.of(Argument.INT))

        then:
        thrown(ConversionErrorException)
    }

    void "the conversions to the wrapper types are unchanged"() {
        given:
        def service = new DefaultMutableConversionService()

        expect:
        service.convert("12", Integer).get() == 12
        service.convert("12", int).get() == 12
        service.convert(new StringBuilder("12"), Integer).get() == 12
        service.convert("x", Integer).isEmpty()
        service.convert("", Integer).isEmpty()
        service.convert("12", Long).get() == 12L
        service.convert("1.5", Double).get() == 1.5d
        service.convert("on", Boolean).get()
        !service.convert("", Boolean).get()
    }

    void "a converter that does not convert to primitives itself is adapted by boxing"() {
        given:
        TypeConverter<String, Integer> plain = { String s, Class<Integer> t, ConversionContext c -> Optional.of(s.length()) } as TypeConverter
        def adapted = PrimitiveTargetTypeConverter.of(plain)

        expect:
        adapted.getInt("abc", ConversionContext.DEFAULT) == 3
        adapted.findInt("ab", ConversionContext.DEFAULT) == OptionalInt.of(2)
        PrimitiveTargetTypeConverter.of(adapted).is(adapted)
    }

    void "a resolved converter converts like the service after a converter is registered"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.INT))

        expect:
        converter.getInt("12", ConversionContext.DEFAULT) == 12

        when: "the built-in converter of the pair is looked up before a custom one"
        service.addConverter(String, Integer, { String s, Class<Integer> t, ConversionContext c -> Optional.of(42) } as TypeConverter)

        then:
        converter.getInt("12", ConversionContext.DEFAULT) == service.convert("12", Integer).get()
        converter.findInt("12", ConversionContext.DEFAULT) == OptionalInt.of(service.convert("12", Integer).get())
    }

    void "a resolved converter converts with a converter registered later for a pair without a built-in one"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = PrimitiveTargetTypeConverter.of(service.resolveConverter(Target, Argument.INT))

        when:
        converter.getInt(new Target(), ConversionContext.DEFAULT)

        then:
        thrown(ConversionErrorException)

        when:
        service.addConverter(Target, Integer, { Target s, Class<Integer> t, ConversionContext c -> Optional.of(42) } as TypeConverter)

        then:
        converter.getInt(new Target(), ConversionContext.DEFAULT) == 42
    }

    void "a resolved converter has a primitive parser only while it holds one"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = PrimitiveTargetTypeConverter.of(service.resolveConverter(String, Argument.INT))

        expect:
        converter.primitiveParser() != null
        converter.primitiveParser().getInt("12", ConversionContext.DEFAULT) == 12
        PrimitiveTargetTypeConverter.of({ String s, Class<Integer> t, ConversionContext c -> Optional.of(1) } as TypeConverter).primitiveParser() == null

        when: "a registrar replaces the built-in parser"
        service.addInternalConverter(CharSequence, Integer, { CharSequence s, Class<Integer> t, ConversionContext c -> Optional.of(42) } as TypeConverter)

        then:
        converter.primitiveParser() == null
        converter.getInt("12", ConversionContext.DEFAULT) == 42
    }

    void "a converter to another type does not convert to the primitive"() {
        given:
        TypeConverter<String, Long> toLong = { String s, Class<Long> t, ConversionContext c -> Optional.of(Long.valueOf(s)) } as TypeConverter
        def adapted = PrimitiveTargetTypeConverter.of(toLong)

        expect:
        adapted.findInt("5", ConversionContext.DEFAULT).isEmpty()
        adapted.getLong("5", ConversionContext.DEFAULT) == 5L

        when:
        adapted.getInt("5", ConversionContext.DEFAULT)

        then:
        thrown(ConversionErrorException)
    }

    void "a resolved converter does not convert to the primitive of another type"() {
        given:
        def converter = PrimitiveTargetTypeConverter.of(new DefaultMutableConversionService().resolveConverter(String, Argument.LONG))

        expect:
        converter.getLong("5", ConversionContext.DEFAULT) == 5L
        converter.findInt("5", ConversionContext.DEFAULT).isEmpty()
        converter.findDouble("5", ConversionContext.DEFAULT).isEmpty()

        when:
        converter.getInt("5", ConversionContext.DEFAULT)

        then:
        thrown(ConversionErrorException)

        when:
        converter.getBoolean("true", ConversionContext.DEFAULT)

        then:
        thrown(ConversionErrorException)
    }

    static class Target {
    }

    void "a value of another type converts like the conversion service converts it"() {
        given:
        def converter = PrimitiveTargetTypeConverter.of(new DefaultMutableConversionService().resolveConverter(CharSequence, Argument.INT))

        expect:
        converter.getInt(new StringBuilder("12"), ConversionContext.DEFAULT) == 12
        converter.getInt("13", ConversionContext.DEFAULT) == 13
    }
}
