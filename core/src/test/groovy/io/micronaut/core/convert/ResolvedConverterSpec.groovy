package io.micronaut.core.convert

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.format.Format
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.core.type.Argument
import spock.lang.Specification

class ResolvedConverterSpec extends Specification {

    void "a resolved converter converts as the service does"() {
        given:
        def service = new DefaultMutableConversionService()
        TypeConverter<String, Object> converter = service.resolveConverter(String, argument)

        expect:
        converter.convert(value, argument.type, ConversionContext.of(argument)) == service.convert(value, ConversionContext.of(argument))
        converter.convert(value, argument.type, ConversionContext.of(argument)) == expected

        where:
        argument                                 | value   | expected
        Argument.INT                             | "10"    | Optional.of(10)
        Argument.of(Integer)                     | "10"    | Optional.of(10)
        Argument.of(Integer)                     | "x"     | Optional.empty()
        Argument.STRING                          | "a"     | Optional.of("a")
        Argument.OBJECT_ARGUMENT                 | "a"     | Optional.of("a")
        Argument.of(CharSequence)                | "a"     | Optional.of("a")
        Argument.of(Optional, Integer)           | "10"    | Optional.of(Optional.of(10))
        Argument.listOf(Integer)                 | "1,2"   | Optional.of([1, 2])
        Argument.of(URI)                         | "http://a" | Optional.of(URI.create("http://a"))
        Argument.of(ResolvedConverterSpec)       | "a"     | Optional.empty()
    }

    void "a resolved converter rejects a value it cannot convert in the context"() {
        given:
        def service = new DefaultMutableConversionService()
        def context = ConversionContext.of(Argument.of(Integer))
        def converter = service.resolveConverter(String, context.getArgument())

        when:
        def result = converter.convert("x", Integer, context)

        then:
        result.isEmpty()
        context.getLastError().isPresent()
    }

    void "a resolved converter converts a null value to nothing"() {
        expect:
        new DefaultMutableConversionService().resolveConverter(String, Argument.of(Integer)).convert(null, Integer, ConversionContext.of(Argument.of(Integer))).isEmpty()
    }

    void "a resolved converter uses a converter registered or removed after it converted"() {
        given:
        def service = new DefaultMutableConversionService()
        def argument = Argument.of(Target)
        def converter = service.resolveConverter(String, argument)

        expect:
        converter.convert("a", Target, ConversionContext.of(argument)).isEmpty()

        when:
        TypeConverter typeConverter = { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s.toString())) } as TypeConverter
        service.addConverter(CharSequence, Target, typeConverter)

        then:
        converter.convert("b", Target, ConversionContext.of(argument)).get().name == "b"

        when:
        service.removeConverter(CharSequence, Target)

        then:
        converter.convert("c", Target, ConversionContext.of(argument)).isEmpty()
    }

    void "a value of another type than the resolved source type is converted by the service"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = service.resolveConverter(CharSequence, Argument.of(Integer))

        expect:
        converter.convert("10", Integer, ConversionContext.of(Argument.of(Integer))).get() == 10
        converter.convert(new StringBuilder("11"), Integer, ConversionContext.of(Argument.of(Integer))).get() == 11
    }

    void "another target type is converted by the service"() {
        given:
        def service = new DefaultMutableConversionService()
        def converter = service.resolveConverter(String, Argument.of(Integer))

        expect:
        converter.convert("10", Integer, ConversionContext.DEFAULT).get() == 10
        ((TypeConverter) converter).convert("10", Long, ConversionContext.DEFAULT).get() == 10L
    }

    void "a resolved converter uses the formatting annotation of the argument"() {
        given:
        def service = new DefaultMutableConversionService()
        AnnotationMetadata metadata = Stub(AnnotationMetadata) {
            hasStereotypeNonRepeating(Format) >> true
            getAnnotationNameByStereotype(Format) >> Optional.of(ReadableBytes.name)
        }
        def formatted = Argument.of(Integer, "size", metadata)
        def plain = Argument.of(Integer, "size")
        def converter = service.resolveConverter(String, formatted)

        expect:
        converter.convert("1KB", Integer, ConversionContext.of(formatted)).get() == 1024
        service.convert("1KB", ConversionContext.of(formatted)).get() == 1024

        and: "a context with other annotation metadata is converted by the service, without the format"
        converter.convert("1KB", Integer, ConversionContext.of(plain)).isEmpty()
        converter.convert("12", Integer, ConversionContext.of(plain)).get() == 12
    }

    void "a subclass of the default conversion service converts every value with its conversion"() {
        given:
        def service = new DefaultMutableConversionService() {
            @Override
            <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
                return Optional.of((T) 42)
            }
        }

        expect:
        service.resolveConverter(String, Argument.of(Integer)).convert("10", Integer, ConversionContext.of(Argument.of(Integer))).get() == 42
    }

    void "the default resolved converter of a conversion service converts with the service"() {
        given:
        ConversionService service = new ConversionService() {
            @Override
            <S, T> Optional<T> convert(S object, Class<? super S> sourceType, Class<T> targetType, ConversionContext context) {
                return Optional.of((T) ("converted:" + object))
            }

            @Override
            <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
                return true
            }
        }

        expect:
        service.resolveConverter(String, Argument.STRING).convert("a", String, ConversionContext.of(Argument.STRING)).get() == "converted:a"
    }

    static class Target {
        final String name

        Target(String name) {
            this.name = name
        }
    }
}
