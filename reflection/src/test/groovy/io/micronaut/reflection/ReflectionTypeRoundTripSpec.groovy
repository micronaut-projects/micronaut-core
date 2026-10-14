package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.type.WildcardArgument

import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.function.Consumer

/**
 * A type read into an {@link Argument} by {@link ReflectionArguments#ofExact(Type)} renders back, through
 * {@link ReflectionArguments#toExactType(Argument)}, as the type it was read from: a wildcard keeps its bounds,
 * a variable every bound it declares, an array its component, a raw type stays bare, and a nested type keeps
 * its owner. The argument read is also the one the processors generate for the same declaration, while
 * {@link ReflectionArguments#of(Type)} and {@link ReflectionArguments#toType(Argument)} keep reading a wildcard
 * as its bound and a generic array as the array of its raw component.
 */
class ReflectionTypeRoundTripSpec extends AbstractTypeElementSpec {

    private static Type typeOf(String field) {
        RoundTripTypes.getDeclaredField(field).genericType
    }

    void "a wildcard bounded above keeps its bound"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf("extendsNumber"))
        def wildcard = argument.typeParameters[0]

        expect: "the argument is of the bound, as the processors compile it"
        argument.type == List
        wildcard instanceof WildcardArgument
        wildcard.type == Number
        wildcard.name == "E"
        ((WildcardArgument) wildcard).upperBounds*.type == [Number]
        ((WildcardArgument) wildcard).lowerBounds.isEmpty()

        and: "and renders back as the type it was read from"
        def rendered = ReflectionArguments.toExactType(argument)
        rendered == typeOf("extendsNumber")
        rendered.hashCode() == typeOf("extendsNumber").hashCode()
        rendered.typeName == "java.util.List<? extends java.lang.Number>"
        ((ParameterizedType) rendered).actualTypeArguments[0] instanceof WildcardType

        and: "or as the type it is bounded by when the caller compares types"
        ReflectionArguments.toType(argument, false) == typeOf("number")
    }

    void "a wildcard bounded below keeps both bounds"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf("superBook"))
        def wildcard = (WildcardArgument) argument.typeParameters[0]

        expect: "the argument is of the lower bound, which is what the processors resolve ? super Book to"
        wildcard.type == Book
        wildcard.lowerBounds*.type == [Book]
        wildcard.upperBounds*.type == [Object]

        and: "and renders back with both"
        def rendered = ReflectionArguments.toExactType(argument)
        rendered == typeOf("superBook")
        rendered.typeName == "java.util.function.Consumer<? super io.micronaut.reflection.Book>"
        ReflectionArguments.toType(argument, false).typeName == "java.util.function.Consumer<io.micronaut.reflection.Book>"
    }

    void "an unbounded wildcard is bounded by Object"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf("unbounded"))
        def wildcard = (WildcardArgument) argument.typeParameters[0]

        expect:
        wildcard.type == Object
        !wildcard.hasLowerBound()
        ReflectionArguments.toExactType(argument) == typeOf("unbounded")
        ReflectionArguments.toExactType(argument).typeName == "java.util.List<?>"
    }

    void "a variable keeps every bound it declares, one of them naming it"() {
        given:
        def method = RoundTripTypes.getDeclaredMethod("several")
        def argument = (GenericPlaceholder) ReflectionArguments.ofExact(method.genericReturnType)

        expect: "the argument erases to the first bound and keeps both"
        argument.type == Comparable
        argument.variableName == "T"
        argument.bounds*.type == [Comparable, Serializable]
        argument.bounds[0].typeParameters[0] instanceof GenericPlaceholder

        when:
        def rendered = (TypeVariable) ReflectionArguments.toExactType(argument)

        then: "the variable renders with both bounds"
        rendered.name == "T"
        rendered.bounds*.typeName == ["java.lang.Comparable<T>", "java.io.Serializable"]
        rendered.typeName == method.genericReturnType.typeName
        rendered.bounds*.typeName == method.genericReturnType.bounds*.typeName

        and: "the variable named within its own bound is the variable itself"
        ((ParameterizedType) rendered.bounds[0]).actualTypeArguments[0].is(rendered)

        and: "a variable rendered twice is equal to itself"
        ReflectionArguments.toExactType(argument) == rendered
        ReflectionArguments.toExactType(argument).hashCode() == rendered.hashCode()

        and: "the erasing rendering is the first bound, the variable inside it erased too"
        ReflectionArguments.toType(argument, false).typeName == "java.lang.Comparable<java.lang.Comparable>"
    }

    void "an array of a variable is a placeholder of the variable"() {
        given:
        def parameter = ReflectionArguments.ofExact(RoundTripTypes.getDeclaredMethod("arrayOfSeveral", Comparable[]).parameters[0].annotatedType)
        def field = ReflectionArguments.ofExact(typeOf("keys"))

        expect:
        parameter instanceof GenericPlaceholder
        parameter.type == Comparable[]
        ((GenericPlaceholder) parameter).bounds*.type == [Comparable, Serializable]
        field instanceof GenericPlaceholder
        field.type == Object[]
        ((GenericPlaceholder) field).variableName == "K"

        and: "and renders as an array of the variable"
        def rendered = ReflectionArguments.toExactType(parameter)
        rendered instanceof GenericArrayType
        rendered.typeName == "T[]"
        ((TypeVariable) ((GenericArrayType) rendered).genericComponentType).bounds*.typeName == ["java.lang.Comparable<T>", "java.io.Serializable"]
        ReflectionArguments.toExactType(field).typeName == "K[]"
        ReflectionArguments.toType(field, false) == Object[]
    }

    void "an array of a parameterized type carries the type arguments of its component"() {
        given:
        def variables = ReflectionArguments.ofExact(typeOf("variableMaps"))
        def strings = ReflectionArguments.ofExact(typeOf("stringMaps"))
        def matrix = ReflectionArguments.ofExact(typeOf("matrix"))

        expect: "the array has the arguments of its component, as the processors write it"
        variables.type == Map[]
        variables.typeParameters*.name == ["K", "V"]
        variables.typeParameters.every { it instanceof GenericPlaceholder }
        strings.type == Map[]
        strings.typeParameters*.type == [String, Integer]
        matrix.type == List[][]
        matrix.typeParameters*.type == [String]

        and: "and renders back as a generic array"
        ReflectionArguments.toExactType(variables).typeName == "java.util.Map<K, V>[]"
        ReflectionArguments.toExactType(variables) instanceof GenericArrayType
        ReflectionArguments.toExactType(strings) == typeOf("stringMaps")
        ReflectionArguments.toExactType(strings).hashCode() == typeOf("stringMaps").hashCode()
        ReflectionArguments.toExactType(matrix) == typeOf("matrix")
        ReflectionArguments.toExactType(matrix).typeName == "java.util.List<java.lang.String>[][]"

    }

    void "a raw type stays bare"() {
        given:
        def argument = ReflectionArguments.ofExact(typeOf("raw"))

        expect: "the argument keeps the variable the type declares, and says it is raw"
        argument.rawType
        argument.typeParameters*.name == ["E"]

        and: "and renders back as the bare class"
        ReflectionArguments.toExactType(argument) == List
        ReflectionArguments.toType(argument, false) == List
    }

    void "a nested type keeps its owner"() {
        given:
        def nested = ReflectionArguments.ofExact(typeOf("nested"))
        def inner = ReflectionArguments.ofExact(typeOf("inner"))

        expect: "a type nested in a bare owner renders back equal"
        nested.type == RoundTripTypes.Outer.Nested
        nested.typeParameters*.type == [Integer]
        ReflectionArguments.toExactType(nested) == typeOf("nested")
        ((ParameterizedType) ReflectionArguments.toExactType(nested)).ownerType == RoundTripTypes.Outer

        and: "an inner type of a parameterized owner renders with the raw owner: an argument carries no owner arguments"
        inner.type == RoundTripTypes.Outer.Inner
        inner.typeParameters*.type == [Integer]
        def rendered = (ParameterizedType) ReflectionArguments.toExactType(inner)
        rendered.rawType == RoundTripTypes.Outer.Inner
        rendered.actualTypeArguments == [Integer] as Type[]
        rendered.ownerType == RoundTripTypes.Outer
        ((ParameterizedType) typeOf("inner")).ownerType instanceof ParameterizedType
    }

    void "a type resolved in place of a variable renders as the type"() {
        given: "the placeholders Argument.of builds for the classes it is given"
        def argument = Argument.of(List, String)

        expect:
        argument.typeParameters[0] instanceof GenericPlaceholder
        ((GenericPlaceholder) argument.typeParameters[0]).resolved
        ReflectionArguments.toExactType(argument) == Types.getDeclaredField("names").genericType
        ReflectionArguments.toExactType(argument).typeName == "java.util.List<java.lang.String>"
        ReflectionArguments.toExactType(Argument.listOf(String)).typeName == "java.util.List<java.lang.String>"
    }

    void "the wildcard read reflectively is the one the processor generates"() {
        given:
        BeanIntrospection<?> generated = buildBeanIntrospection("test.Shelf", '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.function.Consumer;

@Introspected
class Shelf {
    private List<? extends Number> extendsNumber;
    private Consumer<? super String> superString;
    private List<?> unbounded;

    public List<? extends Number> getExtendsNumber() { return extendsNumber; }
    public void setExtendsNumber(List<? extends Number> extendsNumber) { this.extendsNumber = extendsNumber; }
    public Consumer<? super String> getSuperString() { return superString; }
    public void setSuperString(Consumer<? super String> superString) { this.superString = superString; }
    public List<?> getUnbounded() { return unbounded; }
    public void setUnbounded(List<?> unbounded) { this.unbounded = unbounded; }
}
''')

        expect: "the processor generates a wildcard argument with its bounds"
        def extendsNumber = (WildcardArgument) generated.getRequiredProperty("extendsNumber", List).asArgument().typeParameters[0]
        extendsNumber.type == Number
        extendsNumber.upperBounds*.type == [Number]
        extendsNumber.lowerBounds.isEmpty()
        def superString = (WildcardArgument) generated.getRequiredProperty("superString", Consumer).asArgument().typeParameters[0]
        superString.type == String
        superString.lowerBounds*.type == [String]
        superString.upperBounds*.type == [Object]

        and: "and the exact reading of the field is the same"
        ["extendsNumber", "superString", "unbounded"].every { name ->
            def own = (WildcardArgument) generated.getRequiredProperty(name, Object).asArgument().typeParameters[0]
            def other = (WildcardArgument) ReflectionArguments.ofExact(generated.beanType.getDeclaredField(name).annotatedType).typeParameters[0]
            assert other.type == own.type
            assert other.name == own.name
            assert other.upperBounds*.type == own.upperBounds*.type
            assert other.lowerBounds*.type == own.lowerBounds*.type
            assert ReflectionArguments.toExactType(other) == ReflectionArguments.toExactType(own)
            true
        }
    }

    void "the plain conversion keeps reading a wildcard as its bound and a generic array as its raw array"() {
        given:
        def wildcard = ReflectionArguments.of(typeOf("extendsNumber"))
        def lower = ReflectionArguments.of(typeOf("superBook"))
        def array = ReflectionArguments.of(typeOf("variableMaps"))
        def field = ReflectionArguments.of(RoundTripTypes.getDeclaredField("keys"))

        expect: "as it did before the exact conversion existed"
        !(wildcard.typeParameters[0] instanceof WildcardArgument)
        wildcard.typeParameters[0].type == Number
        lower.typeParameters[0].type == Book
        array.type == Map[]
        array.typeParameters.length == 0
        !(field instanceof GenericPlaceholder)
        field.type == Object[]

        and: "and renders a variable with its first bound only"
        def several = ReflectionArguments.ofExact(RoundTripTypes.getDeclaredMethod("several").genericReturnType)
        ((TypeVariable) ReflectionArguments.toType(several)).bounds*.typeName == ["java.lang.Comparable<T>"]
        ReflectionArguments.toType(wildcard) == typeOf("number")
    }

    void "the closure of a type carries its arguments into every type above it"() {
        given:
        def closure = ReflectionArguments.typeClosureOf(Argument.of(RoundTripTypes.Books))

        expect: "the argument first, then each type above it once, Object among them"
        closure[0].type == RoundTripTypes.Books
        closure*.type.unique() == closure*.type
        closure*.type.containsAll([ArrayList, List, Collection, Iterable, Comparable, Object, RandomAccess, Serializable])

        and: "each with the arguments the type gives it"
        ReflectionArguments.toExactType(closure.find { it.type == List }).typeName == "java.util.List<io.micronaut.reflection.Book>"
        ReflectionArguments.toExactType(closure.find { it.type == Iterable }).typeName == "java.lang.Iterable<io.micronaut.reflection.Book>"
        ReflectionArguments.toExactType(closure.find { it.type == Comparable }).typeName == "java.lang.Comparable<io.micronaut.reflection.RoundTripTypes\$Books>"

        and: "the arguments of the argument itself are carried, a wildcard among them"
        def wild = ReflectionArguments.typeClosureOf(ReflectionArguments.ofExact(typeOf("extendsNumber")))
        wild*.type.containsAll([List, Collection, Iterable, Object])
        wild.last().type == Object
        ReflectionArguments.toExactType(wild.find { it.type == Iterable }).typeName == "java.lang.Iterable<? extends java.lang.Number>"

        and: "an interface reaches Object too, and a primitive stands alone"
        ReflectionArguments.typeClosureOf(Argument.of(int))*.type == [int]
        ReflectionArguments.typeClosureOf(Argument.listOf(String)).last().type == Object

        and: "a variable has the super types of the type it is bounded by"
        def variable = ReflectionArguments.typeClosureOf(ReflectionArguments.ofExact(RoundTripTypes.getDeclaredMethod("several").genericReturnType))
        variable[0] instanceof GenericPlaceholder
        variable*.type == [Comparable, Object]
    }
}
