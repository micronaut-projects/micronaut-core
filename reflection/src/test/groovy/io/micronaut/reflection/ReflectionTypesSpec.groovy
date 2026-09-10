package io.micronaut.reflection

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.AnnotationValueProvider
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.annotation.MutableAnnotationMetadata
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

    void "a placeholder keeps every bound the variable declares"() {
        given:
        def arguments = ReflectionArguments.argumentsOf(Types.getDeclaredMethod("several", Comparable, List))

        expect: "the argument still erases to the first bound"
        arguments[0].type == Comparable
        arguments[0] instanceof GenericPlaceholder

        and: "and answers both bounds, the first with its own type arguments"
        ((GenericPlaceholder) arguments[0]).bounds*.type == [Comparable, Cloneable]
        ((GenericPlaceholder) arguments[0]).bounds[0].typeParameters[0].type == String

        and: "the variable inside a type argument keeps them too"
        def inside = arguments[1].typeParameters[0]
        inside instanceof GenericPlaceholder
        ((GenericPlaceholder) inside).bounds*.type == [Comparable, Cloneable]

        and: "and is named as the variable was declared"
        ((GenericPlaceholder) inside).variableName == "T"
    }

    void "a variable inside a type argument is named unlike the parameter it stands in for"() {
        given:
        def argument = ReflectionArguments.argumentsOf(Types.getDeclaredMethod("distinctlyNamed", List))[0]
        def inside = argument.typeParameters[0]

        expect: "the M of List<M>, rather than the E java.util.List declares"
        inside instanceof GenericPlaceholder
        ((GenericPlaceholder) inside).variableName == "M"
        ((GenericPlaceholder) inside).bounds*.type == [Comparable, Cloneable]
        inside.type == Comparable
    }

    void "a variable bounded by a type naming it answers that bound"() {
        given:
        def argument = ReflectionArguments.argumentsOf(Types.getDeclaredMethod("recursive", Comparable))[0]

        expect:
        ((GenericPlaceholder) argument).bounds*.type == [Comparable]
    }

    void "a placeholder with no recorded bounds answers the type it erases to"() {
        given:
        def placeholder = (GenericPlaceholder) Argument.ofTypeVariable(Number, "value")

        expect:
        placeholder.bounds*.type == [Number]

        and: "the answer is the same list every time"
        placeholder.bounds.is(placeholder.bounds)

        and: "and renaming keeps the variable it stands for"
        ((GenericPlaceholder) placeholder.withName("other")).variableName == "value"
    }

    void "a placeholder renders as the type it is bounded by when the caller compares types"() {
        given:
        def method = Types.getDeclaredMethod("identity", Object)
        def argument = ReflectionArguments.argumentsOf(method)[0]

        expect: "the erasing rendering, which an assignability check wants"
        ReflectionArguments.toType(argument, false) == Object
        !(ReflectionArguments.toType(argument, false) instanceof TypeVariable)

        and: "a parameterized type keeps its parameters either way, the variables inside it following the choice"
        def names = ReflectionArguments.of(Types.getDeclaredField("names"))
        ReflectionArguments.toType(names, false) == Types.getDeclaredField("names").genericType
        ReflectionArguments.toType(names, true) == Types.getDeclaredField("names").genericType
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

    void "an annotation type is declared with its defaults and its stereotypes, without an instance"() {
        when: "a container adapting another one knows the type and the members it means"
        def metadata = ReflectionAnnotations.declaring(Restricted, [level: 3])

        then:
        metadata.hasDeclaredAnnotation(Restricted)
        metadata.intValue(Restricted, "level").get() == 3

        and: "the members it did not give are carried in the defaults, where the generated metadata carries them"
        metadata.getDefaultValues(Restricted.name).get("name") == "unnamed"
        !metadata.stringValue(Restricted, "name").present

        and: "a type declared bare carries its defaults all the same"
        ReflectionAnnotations.declaring(Restricted).getDefaultValues(Restricted.name).get("level") == 1
    }

    void "an annotation type is declared into a metadata under construction"() {
        given:
        def metadata = new MutableAnnotationMetadata()

        when:
        ReflectionAnnotations.declare(metadata, Restricted, [level: 5])

        then:
        metadata.hasDeclaredAnnotation(Restricted)
        metadata.intValue(Restricted, "level").get() == 5
        metadata.getDefaultValues(Restricted.name).get("name") == "unnamed"
    }

    void "a repeatable annotation type is declared under its container, with its stereotypes"() {
        when:
        def metadata = ReflectionAnnotations.declaring(Tag, [value: "declared"])

        then: "it is found the way the generated metadata is read"
        metadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["declared"]

        and: "the meta annotations are stereotypes, so a qualifier built from it matches one built from generated metadata"
        metadata.hasStereotype(Stereo)
        metadata.stringValue(Stereo, "kind").get() == "tag"
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
