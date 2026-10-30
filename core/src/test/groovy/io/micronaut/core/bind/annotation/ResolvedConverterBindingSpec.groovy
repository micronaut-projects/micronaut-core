package io.micronaut.core.bind.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap
import io.micronaut.core.convert.value.MutableConvertibleMultiValuesMap
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.type.Argument
import spock.lang.Specification

class ResolvedConverterBindingSpec extends Specification {

    void "a specific binder binds a single string value as the general binder does"() {
        given:
        def service = new DefaultMutableConversionService()
        def values = new ConvertibleMultiValuesMap<String>([value: [value]], service)

        when:
        def specific = new TestBinder(service, argument).bind(ConversionContext.of(argument), values)
        def general = new TestBinder(service).bind(ConversionContext.of(argument), values)

        then:
        specific.getValue() == expected
        specific.getValue() == general.getValue()
        specific.isConvertedToArgumentType() == general.isConvertedToArgumentType()
        specific.getConversionErrors().size() == general.getConversionErrors().size()

        where:
        argument                                                  | value  | expected
        Argument.of(int, "value")                                 | "10"   | Optional.of(10)
        Argument.of(String, "value")                              | "a"    | Optional.of("a")
        Argument.of(List, "value", Argument.of(Integer))          | "1,2"  | Optional.of([1, 2])
        Argument.of(Set, "value", Argument.of(Integer))           | "1,2"  | Optional.of([1, 2] as Set)
        Argument.of(Optional, "value", Argument.of(Long))         | "5"    | Optional.of(5L)
        Argument.of(Optional, "value", Argument.of(Long))         | "x"    | Optional.empty()
        Argument.of(Integer, "value")                             | "x"    | Optional.empty()
    }

    void "values with another conversion service convert with their service"() {
        given:
        def binderService = new DefaultMutableConversionService()
        def valuesService = new DefaultMutableConversionService()
        valuesService.addConverter(String, Target, { String s, Class<Target> t, ConversionContext c -> Optional.of(new Target(s)) } as TypeConverter)
        def argument = Argument.of(Target, "value")
        def values = new ConvertibleMultiValuesMap<String>([value: ["a"]], valuesService)

        when:
        def specific = new TestBinder(binderService, argument).bind(ConversionContext.of(argument), values)
        def general = new TestBinder(binderService).bind(ConversionContext.of(argument), values)

        then:
        specific.getValue().get().name == "a"
        general.getValue().get().name == "a"
    }

    void "values convert a single string value with the converter of their conversion service"() {
        given:
        def service = new DefaultMutableConversionService()
        def argument = Argument.of(Integer, "value")
        def values = new ConvertibleMultiValuesMap<String>([one: ["10"], many: ["1", "2"], none: []], service)
        TypeConverter<String, Integer> converter = { String s, Class<Integer> t, ConversionContext c -> Optional.of(42) } as TypeConverter

        expect: "a single string value is converted with the converter, others as before"
        values.get("one", ConversionContext.of(argument), converter).get() == 42
        values.get("one", ConversionContext.of(argument)).get() == 10
        values.get("many", ConversionContext.of(argument), converter) == values.get("many", ConversionContext.of(argument))
        values.get("missing", ConversionContext.of(argument), converter).isEmpty()
    }

    void "values that override the lookup of a value bind the value they look up"() {
        given:
        def service = new DefaultMutableConversionService()
        def argument = Argument.of(Integer, "value")
        def values = new ConvertibleMultiValuesMap<String>([value: ["10"]], service) {
            @Override
            <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
                return Optional.of((T) 42)
            }
        }

        expect:
        new TestBinder(service, argument).bind(ConversionContext.of(argument), values).getValue() == Optional.of(42)
        new TestBinder(service).bind(ConversionContext.of(argument), values).getValue() == Optional.of(42)
    }

    void "mutable multi values convert a single string value with the converter"() {
        given:
        def service = new DefaultMutableConversionService()
        def argument = Argument.of(Integer, "value")
        def values = new MutableConvertibleMultiValuesMap<String>([value: ["10"]], service)
        TypeConverter<String, Integer> converter = { String s, Class<Integer> t, ConversionContext c -> Optional.of(42) } as TypeConverter

        expect:
        values.get("value", ConversionContext.of(argument), converter).get() == 42
    }

    void "a binder that overrides the resolution binds the value it resolves"() {
        given:
        def service = new DefaultMutableConversionService()
        def argument = Argument.of(Integer, "value")
        def values = new ConvertibleMultiValuesMap<String>([value: ["10"]], service)

        expect:
        new ResolvingBinder(service, argument).bind(ConversionContext.of(argument), values).getValue() == Optional.of(42)
        new ResolvingBinder(service).bind(ConversionContext.of(argument), values).getValue() == Optional.of(42)
    }

    void "a single value the specific binder cannot convert binds the default value"() {
        given:
        def service = new DefaultMutableConversionService()
        AnnotationMetadata metadata = Stub(AnnotationMetadata) {
            stringValue(Bindable, "defaultValue") >> Optional.of("7")
            isPresent(Bindable, "defaultValue") >> true
        }
        def argument = Argument.of(Integer, "value", metadata)
        def values = new ConvertibleMultiValuesMap<String>([value: ["x"]], service)

        when:
        def specific = new TestBinder(service, argument).bind(ConversionContext.of(argument), values)
        def general = new TestBinder(service).bind(ConversionContext.of(argument), values)

        then:
        specific.getValue() == Optional.of(7)
        specific.getValue() == general.getValue()
        specific.getConversionErrors().size() == general.getConversionErrors().size()
    }

    void "a missing value binds the default value with the resolved converter"() {
        given:
        def service = new DefaultMutableConversionService()
        AnnotationMetadata metadata = Stub(AnnotationMetadata) {
            stringValue(Bindable, "defaultValue") >> Optional.of("1,2")
            isPresent(Bindable, "defaultValue") >> true
        }
        def argument = Argument.of(List, "value", metadata, Argument.of(Integer))
        def values = new ConvertibleMultiValuesMap<String>([:], service)

        expect:
        new TestBinder(service, argument).bind(ConversionContext.of(argument), values).getValue() == Optional.of([1, 2])
        new TestBinder(service).bind(ConversionContext.of(argument), values).getValue() == Optional.of([1, 2])
    }

    static class TestBinder<T> extends AbstractArgumentBinder<T> {

        TestBinder(ConversionService conversionService) {
            super(conversionService)
        }

        TestBinder(ConversionService conversionService, Argument<T> argument) {
            super(conversionService, argument)
        }

        ArgumentBinder.BindingResult<T> bind(ArgumentConversionContext<T> context, ConvertibleValues<?> values) {
            return doBind(context, values)
        }

        @Override
        protected String getParameterName(Argument<T> argument) {
            return argument.getName()
        }
    }

    static class Target {
        final String name

        Target(String name) {
            this.name = name
        }
    }

    static class ResolvingBinder<T> extends TestBinder<T> {

        ResolvingBinder(ConversionService conversionService) {
            super(conversionService)
        }

        ResolvingBinder(ConversionService conversionService, Argument<T> argument) {
            super(conversionService, argument)
        }

        @Override
        protected Object doResolve(ArgumentConversionContext<T> context, ConvertibleValues<?> values, String name) {
            return 42
        }
    }
}
