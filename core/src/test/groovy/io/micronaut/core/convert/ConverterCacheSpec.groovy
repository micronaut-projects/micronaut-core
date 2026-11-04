package io.micronaut.core.convert

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ConverterCacheSpec extends Specification {

    void "a source type found unconvertible converts once a converter for its super type is registered"() {
        given:
        def service = new DefaultMutableConversionService()

        expect:
        !service.canConvert(StringBuilder, Target)
        service.convert(new StringBuilder("a"), Target).isEmpty()

        when:
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s.toString())) } as TypeConverter)

        then:
        service.canConvert(StringBuilder, Target)
        service.convert(new StringBuilder("b"), Target).get().name == "b"
    }

    void "a source type converted with the converter of a super type takes a converter registered for a closer one"() {
        given:
        def service = new DefaultMutableConversionService()
        service.addConverter(Object, Target, { Object s, Class<Target> t, ConversionContext c -> Optional.of(new Target("object:" + s)) } as TypeConverter)

        expect:
        service.convert(new StringBuilder("a"), Target).get().name == "object:a"

        when:
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target("cs:" + s)) } as TypeConverter)

        then:
        service.convert(new StringBuilder("b"), Target).get().name == "cs:b"
    }

    void "a resolved converter takes a converter registered for a super type of its source type"() {
        given:
        def service = new DefaultMutableConversionService()
        TypeConverter<StringBuilder, Target> converter = service.resolveConverter(StringBuilder, Argument.of(Target))

        expect:
        converter.convert(new StringBuilder("a"), Target, ConversionContext.of(Target)).isEmpty()

        when:
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s.toString())) } as TypeConverter)

        then:
        converter.convert(new StringBuilder("b"), Target, ConversionContext.of(Target)).get().name == "b"
    }

    void "a lookup that found a converter before its removal does not cache it after the removal"() {
        given:
        def service = new PausingConversionService()
        service.addConverter(CharSequence, Target, { CharSequence s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s.toString())) } as TypeConverter)
        service.pause = true
        Optional<Target> converted = null
        def lookup = Thread.start {
            converted = service.convert(new StringBuilder("a"), Target)
        }

        when: "the converter is removed while the lookup holds it"
        assert service.found.await(10, TimeUnit.SECONDS)
        service.removeConverter(CharSequence, Target)
        service.resume.countDown()
        lookup.join(10_000)

        then: "the lookup converts with what it found, and the removed converter is not cached"
        converted.get().name == "a"
        service.convert(new StringBuilder("b"), Target).isEmpty()
        !service.canConvert(StringBuilder, Target)
    }

    void "a converter registered for a primitive source type converts it"() {
        given:
        def service = new DefaultMutableConversionService()
        service.addConverter(int, Target, { Integer i, Class<Target> t, ConversionContext c -> Optional.of(new Target("int:" + i)) } as TypeConverter)

        expect:
        service.canConvert(int, Target)
        service.convert(1, int, Target, ConversionContext.DEFAULT).get().name == "int:1"
    }

    void "a resolved converter does not keep a removed converter of another pair"() {
        given:
        def service = new DefaultMutableConversionService()
        def removed = new WeakReference<TypeConverter>(registerAndUse(service))
        TypeConverter<String, Integer> converter = service.resolveConverter(String, Argument.of(Integer))

        expect:
        converter.convert("1", Integer, ConversionContext.of(Integer)).get() == 1

        when:
        service.removeConverter(StringBuilder, Target)
        for (int i = 0; i < 20 && removed.get() != null; i++) {
            System.gc()
            Thread.sleep(50)
        }

        then:
        removed.get() == null
        converter != null
    }

    private static TypeConverter registerAndUse(DefaultMutableConversionService service) {
        TypeConverter typeConverter = new BuilderConverter()
        service.addConverter(StringBuilder, Target, typeConverter)
        assert service.convert(new StringBuilder("a"), Target).get().name == "a"
        return typeConverter
    }

    static class BuilderConverter implements TypeConverter<StringBuilder, Target> {
        @Override
        Optional<Target> convert(StringBuilder object, Class<Target> targetType, ConversionContext context) {
            return Optional.of(new Target(object.toString()))
        }
    }

    static class PausingConversionService extends DefaultMutableConversionService {
        volatile boolean pause
        final CountDownLatch found = new CountDownLatch(1)
        final CountDownLatch resume = new CountDownLatch(1)

        @Override
        protected <T> TypeConverter<Object, T> findTypeConverter(Class<?> sourceType, Class<T> targetType, @Nullable String formattingAnnotation) {
            def converter = super.findTypeConverter(sourceType, targetType, formattingAnnotation)
            if (pause && sourceType == StringBuilder) {
                pause = false
                found.countDown()
                resume.await(10, TimeUnit.SECONDS)
            }
            return converter
        }
    }

    static class Target {
        final String name
        Target(String name) { this.name = name }
    }
}
