package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.lang.reflect.TypeVariable

class ReflectionTypesSpec extends Specification {

    void "an argument renders as the type reflection reports"() {
        given:
        def field = Types.getDeclaredField("names")
        def argument = ReflectionArguments.of(field)

        expect:
        ReflectionArguments.toType(argument) == field.genericType
        ReflectionArguments.toType(argument).hashCode() == field.genericType.hashCode()
        ReflectionArguments.toType(argument) instanceof ParameterizedType
        ReflectionArguments.toType(argument).typeName == "java.util.List<java.lang.String>"
        ReflectionArguments.toType(Argument.of(String)) == String
    }

    void "a placeholder renders as a type variable"() {
        given:
        def method = Types.getDeclaredMethod("identity", Object)
        def argument = ReflectionArguments.argumentsOf(method)[0]

        expect:
        argument.typeVariable
        argument instanceof GenericPlaceholder
        ((GenericPlaceholder) argument).variableName == "T"
        argument.type == Object
        argument.name == "value"

        when:
        def type = ReflectionArguments.toType(argument)

        then:
        type instanceof TypeVariable
        type.name == "T"
        type.bounds == [Object] as Object[]
    }

    void "a type is converted tolerantly"() {
        expect: "a generic array is the array of its raw component"
        ReflectionArguments.of(Types.getDeclaredField("matrix").genericType).type == List[]

        and: "a wildcard is its upper bound"
        ReflectionArguments.of(Types.getDeclaredField("numbers").genericType).typeParameters[0].type == Number
        ReflectionArguments.of(Types.getDeclaredField("anything").genericType).typeParameters[1].type == Object

        and: "a type variable is a placeholder of its bound"
        ReflectionArguments.of(Types.getDeclaredMethod("identity", Object).genericReturnType).typeVariable

        and: "a name can be given"
        ReflectionArguments.of("names", Types.getDeclaredField("names").genericType).name == "names"
    }

    void "an annotation is synthesized from a value and converts back to it"() {
        given:
        def value = ReflectionAnnotations.valueOf(Types.getAnnotation(Tag))

        when:
        def tag = ReflectionAnnotations.synthesize(Tag, value)

        then:
        tag.value() == "types"
        tag.annotationType() == Tag
        tag instanceof AnnotationValueProvider
        ReflectionAnnotations.valueOf(tag).is(value)

        and: "the type can be resolved by name"
        ReflectionAnnotations.synthesize(value, getClass().classLoader).value() == "types"

        when:
        ReflectionAnnotations.synthesize(value, new ClassLoader(null) {})

        then:
        thrown(IllegalArgumentException)
    }

    void "the non binding members of a qualifier are recorded as the processors record them"() {
        given:
        def value = ReflectionAnnotations.valueOf(Types.getDeclaredField("bound").getAnnotation(Binding))

        expect:
        value.stringValue().get() == "b"
        value.stringValue("comment").get() == "ignored"
        value.get(AnnotationUtil.NON_BINDING_ATTRIBUTE, String[]).get() == ["comment", AnnotationUtil.NON_BINDING_ATTRIBUTE] as String[]
    }

    void "annotation instances become declared metadata"() {
        when:
        def metadata = ReflectionAnnotations.metadataOf(Types.getAnnotations())

        then:
        metadata.hasDeclaredAnnotation(Tag)
        metadata.stringValue(Tag).get() == "types"

        and:
        ReflectionAnnotations.metadataOf(new java.lang.annotation.Annotation[0]).empty
    }
}
