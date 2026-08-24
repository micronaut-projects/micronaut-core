package io.micronaut.inject.reflection

import io.micronaut.context.AnnotationReflectionUtils
import spock.lang.Specification

class ReflectionArgumentFactoriesSpec extends Specification {

    private static List<String> tags(def argument) {
        argument.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get()
    }

    void "the argument of a field is named after it and carries the type-use annotations of every level"() {
        when:
        def argument = AnnotationReflectionUtils.argumentOf(Factories.getDeclaredField("nested"))

        then:
        argument.name == "nested"
        argument.type == Map

        and: "the annotation on the value type, and the one nested inside it"
        tags(argument.typeParameters[0]) == []
        tags(argument.typeParameters[1]) == ["mapValue"]
        tags(argument.typeParameters[1].typeParameters[0]) == ["deep"]
    }

    void "the argument of a parameter is named after it and carries its annotations and its type-use ones"() {
        given:
        def method = Factories.getMethod("produce", Map)

        when:
        def argument = AnnotationReflectionUtils.argumentOf(method.parameters[0])

        then: "the annotation declared on the parameter"
        argument.name == "input"
        argument.type == Map
        tags(argument) == ["param"]

        and: "and the one declared on its type argument"
        tags(argument.typeParameters[1]) == ["arg"]
    }

    void "the arguments of a method and of a constructor are the arguments of their parameters"() {
        expect:
        AnnotationReflectionUtils.argumentsOf(Factories.getMethod("produce", Map))*.name == ["input"]
        AnnotationReflectionUtils.argumentsOf(Factories.getMethod("consume", String))*.type == [String]

        and: "a constructor is read the same way"
        def constructor = AnnotationReflectionUtils.argumentsOf(Factories.getConstructor(String, List))
        tags(constructor[0]) == ["ctorParam"]
        tags(constructor[1].typeParameters[0]) == ["ctorElem"]

        and: "an executable with no parameter has no argument"
        AnnotationReflectionUtils.argumentsOf(Factories.getConstructor()).length == 0
        AnnotationReflectionUtils.argumentsOf(Factories.getConstructor()).is(io.micronaut.core.type.Argument.ZERO_ARGUMENTS)
    }

    void "the return argument of a method is the annotated return type, not the method"() {
        when:
        def argument = AnnotationReflectionUtils.returnArgumentOf(Factories.getMethod("produce", Map))

        then: "the type-use annotation of the returned type argument"
        argument.type == List
        tags(argument.typeParameters[0]) == ["returned"]

        and: "an annotation written before the return type whose target includes TYPE_USE is one of that type too, the way the compiler records it"
        tags(argument) == ["method"]

        and: "a void method returns a void argument"
        AnnotationReflectionUtils.returnArgumentOf(Factories.getMethod("consume", String)).type == void
    }

    void "an argument can be named by the caller rather than by the member"() {
        when:
        def named = AnnotationReflectionUtils.argumentOf("chosen", Factories.getDeclaredField("plain").annotatedType)
        def anonymous = AnnotationReflectionUtils.argumentOf(Factories.getDeclaredField("plain").annotatedType)

        then:
        named.name == "chosen"
        named.type == String
        anonymous.type == String
    }
}
