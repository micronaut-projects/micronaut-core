package io.micronaut.inject.reflection

import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.reflect.Field
import java.lang.reflect.Method

class ReflectionBeanIntrospectionSpec extends Specification {

    void "the constructor is selected the way the processor selects it"() {
        expect:
        ReflectionBeanIntrospection.of(type).constructorArguments*.type == arguments

        where:
        type                              || arguments
        Constructors.Annotated            || [String, int]   // @Creator wins, though it is not public
        Constructors.OnlyPublic           || [String, int]   // the only public one
        Constructors.NoArgAmongMany       || []              // among several public ones, the no-arg one
        Constructors.NonePublic           || [String, int]   // none public: the widest declared one
    }

    void "every declared constructor is listed, the selected one first"() {
        when:
        def constructors = ReflectionBeanIntrospection.of(Constructors.Annotated).constructors

        then: "the selected one, then the others, without repeating it"
        constructors*.arguments*.type == [[String, int], [], [String]]
    }

    void "instantiation reports what it cannot do"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book)

        expect:
        introspection.isBuildable()

        when: "the wrong number of arguments"
        introspection.instantiate("only")

        then:
        def wrongCount = thrown(InstantiationException)
        wrongCount.message.contains("expects 2 argument(s), 1 given")

        when: "null for an argument that is not nullable, checked strictly"
        introspection.instantiate(true, null, 1)

        then:
        thrown(InstantiationException)

        when: "the same, not checked strictly, reaches the constructor"
        def book = introspection.instantiate(false, null, 1)

        then:
        book.pages == 1
    }

    void "an interface declares no constructor a reflective introspection can invoke"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Shelf)

        expect:
        !introspection.isBuildable()

        when:
        introspection.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message.contains("declares no constructor")
    }

    void "the types a reflective introspection describes"() {
        expect:
        ReflectionBeanIntrospection.isIntrospectable(Book)
        ReflectionBeanIntrospection.isIntrospectable(Shelf)          // an interface, for its declarations
        !ReflectionBeanIntrospection.isIntrospectable(int)
        !ReflectionBeanIntrospection.isIntrospectable(String[])
        !ReflectionBeanIntrospection.isIntrospectable(Level)          // an enum
        !ReflectionBeanIntrospection.isIntrospectable(Tag)            // an annotation
        !ReflectionBeanIntrospection.isIntrospectable(String)         // java.
        !ReflectionBeanIntrospection.isIntrospectable(List)           // java.
    }

    void "the properties are indexed by the stereotypes and the values of their annotations"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Shadowed)

        expect: "every property whose metadata carries the stereotype"
        introspection.getIndexedProperties(Stereo)*.name == ["value"]
        introspection.getIndexedProperties(Hidden)*.name == ["marked"]
        introspection.getIndexedProperties(Shape).isEmpty()

        and: "the property whose annotation holds the value"
        introspection.getIndexedProperty(Hidden, "indexed").get().name == "marked"
        introspection.getIndexedProperty(Hidden, "absent").empty
    }

    void "a property is made of every member declaring it, the most specific first"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Shadowed)

        when:
        def members = introspection.getPropertyMembers("value")

        then: "the shadowed field of the super class is a member too, after the one of the type"
        members.findAll { it.member instanceof Field }*.declaringType == [Shadowed, ShadowedBase]
        members.findAll { it.member instanceof Field }*.annotationMetadata
                *.getAnnotationValuesByType(Tag)*.collect { it.stringValue().get() } == [["sub"], ["super"]]

        and: "the getter and the setter, each with its own metadata and element type"
        def getter = members.find { it.member instanceof Method && ((Method) it.member).parameterCount == 0 }
        def setter = members.find { it.member instanceof Method && ((Method) it.member).parameterCount == 1 }
        getter.elementType == ElementType.METHOD
        getter.annotationMetadata.getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["getter"]
        setter.elementType == ElementType.METHOD
        members.findAll { it.member instanceof Field }*.elementType.every { it == ElementType.FIELD }

        and: "a field and a getter yield the property value, a setter does not"
        getter.readable
        !setter.readable
        members.findAll { it.member instanceof Field }.every { it.readable }

        and: "an unknown property has no member"
        introspection.getPropertyMembers("missing").isEmpty()
    }

    void "a member reads the value it holds, and says so when it cannot"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Shadowed)
        def bean = new Shadowed("own")
        def members = introspection.getPropertyMembers("value")

        expect: "the field of the type and its getter read the value of the type"
        members.find { it.member instanceof Field && it.declaringType == Shadowed }.read(bean) == "own"
        members.find { it.member instanceof Method && ((Method) it.member).parameterCount == 0 }.read(bean) == "own"

        and: "the shadowed field of the super class reads what that field holds"
        members.find { it.member instanceof Field && it.declaringType == ShadowedBase }.read(bean) == "inherited"

        when: "a setter is asked for the value"
        members.find { it.member instanceof Method && ((Method) it.member).parameterCount == 1 }.read(bean)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("does not yield the property value")
    }

    void "the builder sets the constructor arguments by name, by index, from an existing bean and by conversion"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book)

        expect: "the arguments it builds from, and their positions"
        introspection.builder().builderArguments*.type == [String, int]
        introspection.builder().buildMethodArguments.length == 0
        introspection.builder().indexOf("title") == 0
        introspection.builder().indexOf("pages") == 1
        introspection.builder().indexOf("missing") == -1

        when: "by name"
        def built = introspection.builder().with("title", "Named").with("pages", 5).build()

        then:
        built.title == "Named"
        built.pages == 5

        when: "an argument that does not exist"
        introspection.builder().with("missing", "x")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("No constructor argument named 'missing'")

        when: "from an existing bean, then overridden by index"
        def copied = introspection.builder()
                .with(new Book("Existing", 9))
                .with(0, Argument.of(String, "title"), "Overridden")
                .build()

        then:
        copied.title == "Overridden"
        copied.pages == 9

        when: "by conversion"
        def converted = introspection.builder()
                .with("title", "Converted")
                .convert(1, ConversionContext.of(Argument.of(int, "pages")), "12", ConversionService.SHARED)
                .build()

        then:
        converted.pages == 12

        and: "an argument left unset is null, which build does not check"
        introspection.builder().with("pages", 1).build().title == null

        and: "build with method arguments builds the same bean: there is no build method"
        introspection.builder().with("title", "Same").with("pages", 2).build("ignored").title == "Same"
    }
}
