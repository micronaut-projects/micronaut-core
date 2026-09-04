package io.micronaut.reflection

import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Specification

import java.util.function.Consumer

class ReflectionArgumentFactoriesSpec extends Specification {

    private static List<String> tags(def argument) {
        argument.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get()
    }

    void "the argument of a field is named after it and carries the type-use annotations of every level"() {
        when:
        def argument = ReflectionArguments.of(Factories.getDeclaredField("nested"))

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
        def argument = ReflectionArguments.of(method.parameters[0])

        then: "the annotation declared on the parameter"
        argument.name == "input"
        argument.type == Map
        tags(argument) == ["param"]

        and: "and the one declared on its type argument"
        tags(argument.typeParameters[1]) == ["arg"]
    }

    void "the arguments of a method and of a constructor are the arguments of their parameters"() {
        expect:
        ReflectionArguments.argumentsOf(Factories.getMethod("produce", Map))*.name == ["input"]
        ReflectionArguments.argumentsOf(Factories.getMethod("consume", String))*.type == [String]

        and: "a constructor is read the same way"
        def constructor = ReflectionArguments.argumentsOf(Factories.getConstructor(String, List))
        tags(constructor[0]) == ["ctorParam"]
        tags(constructor[1].typeParameters[0]) == ["ctorElem"]

        and: "an executable with no parameter has no argument"
        ReflectionArguments.argumentsOf(Factories.getConstructor()).length == 0
        ReflectionArguments.argumentsOf(Factories.getConstructor()).is(io.micronaut.core.type.Argument.ZERO_ARGUMENTS)
    }

    void "the return argument of a method is the annotated return type, not the method"() {
        when:
        def argument = ReflectionArguments.returnOf(Factories.getMethod("produce", Map))

        then: "the type-use annotation of the returned type argument"
        argument.type == List
        tags(argument.typeParameters[0]) == ["returned"]

        and: "an annotation written before the return type whose target includes TYPE_USE is one of that type too, the way the compiler records it"
        tags(argument) == ["method"]

        and: "a void method returns a void argument"
        ReflectionArguments.returnOf(Factories.getMethod("consume", String)).type == void
    }

    void "an argument can be named by the caller rather than by the member"() {
        when:
        def named = ReflectionArguments.of("chosen", Factories.getDeclaredField("plain").annotatedType)
        def anonymous = ReflectionArguments.of(Factories.getDeclaredField("plain").annotatedType)

        then:
        named.name == "chosen"
        named.type == String
        anonymous.type == String
    }

    void "a bound naming the variable it bounds is converted once rather than for ever"() {
        when: "a method whose variable is bounded by a type naming that same variable"
        def arguments = ReflectionArguments.argumentsOf(ArgGenerics.getDeclaredMethod("max", List))

        then: "the variable is a placeholder of the erasure of its bound"
        arguments[0].type == List
        def element = arguments[0].typeParameters[0]
        element.type == Comparable
        element.typeVariable
        ((GenericPlaceholder) element).variableName == "T"

        and: "inside its own bound the variable stands for that erasure, so the conversion terminates"
        element.typeParameters[0].type == Comparable
        element.typeParameters[0].typeParameters.length == 0

        and: "an enum constant bounded by itself is read the same way"
        ReflectionArguments.argumentsOf(ArgGenerics.getDeclaredMethod("constant", Enum))[0].type == Enum

        and: "so is a field whose type is bounded by the type declaring it"
        ReflectionArguments.of(ArgGenerics.Node.getDeclaredField("next")).type == ArgGenerics.Node
    }

    void "a wildcard is the type it is bounded by, the lower bound first"() {
        expect: "`? super String` is String, which is what the processors resolve it to"
        ReflectionArguments.argumentsOf(ArgGenerics.getDeclaredMethod("receive", Consumer))[0]
                .typeParameters[0].type == String

        and: "an upper bound is still the bound"
        ReflectionArguments.argumentsOf(ArgGenerics.getDeclaredMethod("supply", List))[0]
                .typeParameters[0].type == Number

        and: "an unbounded wildcard is Object"
        ReflectionArguments.argumentsOf(ArgGenerics.getDeclaredMethod("anything", List))[0]
                .typeParameters[0].type == Object
    }

    void "a member is read as the type reading it sees it"() {
        given:
        def field = ArgGenerics.Base.getDeclaredField("dependency")
        def setter = ArgGenerics.Base.getDeclaredMethod("accept", Object)
        def getter = ArgGenerics.Base.getDeclaredMethod("produce")

        expect: "the variable the reading type gives a value to is resolved, through as many levels as there are"
        ReflectionArguments.of(field, ArgGenerics.Impl).type == String
        ReflectionArguments.of(setter.parameters[0], ArgGenerics.Impl).type == String
        ReflectionArguments.argumentsOf(setter, ArgGenerics.Impl)*.type == [String]
        ReflectionArguments.returnOf(getter, ArgGenerics.Impl).typeParameters[0].type == String

        and: "a collection of the variable is resolved inside its type arguments"
        ReflectionArguments.of(ArgGenerics.Base.getDeclaredField("all"), ArgGenerics.Impl)
                .typeParameters[0].type == String

        and: "an interface gives its variable a value the same way"
        ReflectionArguments.returnOf(ArgGenerics.Box.getDeclaredMethod("value"), ArgGenerics.StringBox).type == String

        and: "read through the type declaring it the variable stays a placeholder, as the processors generate it there"
        ReflectionArguments.of(field, ArgGenerics.Base).type == Object
        ReflectionArguments.of(field, ArgGenerics.Base).typeVariable

        and: "a type that does not implement the declaring type resolves nothing"
        ReflectionArguments.of(field, String).type == Object
    }
}
