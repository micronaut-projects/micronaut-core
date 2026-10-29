package io.micronaut.core.bind.annotation

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.convert.value.ConvertibleMultiValuesMap
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

    void "a value the resolved converter converted is not converted again"() {
        given:
        def service = new DefaultMutableConversionService()
        def converted = [1, 2]
        service.addConverter(String, List, { String s, Class<List> t, ConversionContext c -> Optional.of(converted) } as TypeConverter)
        def argument = Argument.of(List, "value", Argument.of(Integer))
        def values = new ConvertibleMultiValuesMap<String>([value: ["anything"]], service)

        when:
        def specific = new TestBinder(service, argument).bind(ConversionContext.of(argument), values)
        def general = new TestBinder(service).bind(ConversionContext.of(argument), values)

        then: "the specific binder keeps the list, the general one converts it to a copy"
        specific.getValue().get().is(converted)
        general.getValue().get() == converted
        !general.getValue().get().is(converted)
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
}
