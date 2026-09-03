package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected
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

    void "the read and write property views are the ones a caller reading a bean asks for"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Book)

        expect: "every property that yields a value is a read property, every one that takes one is a write property"
        introspection.beanReadProperties*.name.toSet() == introspection.beanProperties.findAll { !it.writeOnly }*.name.toSet()
        introspection.beanWriteProperties*.name.toSet() == introspection.beanProperties.findAll { !it.readOnly }*.name.toSet()
        !introspection.beanReadProperties.empty

        and: "they are the properties themselves, so they read and write what the property does"
        def title = introspection.beanReadProperties.find { it.name == "title" }
        title.get(new Book("t", 1)) == "t"
    }

    void "a property declared by a generic type is of the type the bean type gives it"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(IntroStringBox)

        expect: "the variable the super class leaves open is the type the sub class passes, as a generated introspection reads it"
        introspection.getProperty("value").get().type == String

        and: "every member of the property is read through the bean type, so all of them agree"
        def members = introspection.getPropertyMembers("value")
        members.size() == 3
        members*.argument*.type.every { it == String }

        and: "and the property is read and written through the members the super class declares"
        def box = new IntroStringBox()
        introspection.getRequiredProperty("value", String).set(box, "boxed")
        introspection.getRequiredProperty("value", String).get(box) == "boxed"

        and: "a type argument of that type is resolved too"
        def nested = ReflectionBeanIntrospection.of(IntroStringListBox).getProperty("value").get()
        nested.type == List
        nested.asArgument().typeParameters*.type == [String]

        and: "the getter of a generic interface is read through the type that gives the variable its type"
        ReflectionBeanIntrospection.of(IntroBoxView).getProperty("value").get().type == String
    }

    void "a field alone is a property only when field access is asked for"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(IntroSecrets)

        expect: "the property the accessors declare, and nothing the type keeps to itself"
        introspection.beanProperties*.name.toSet() == ["name"].toSet()

        and: "a private field with no accessor is neither a property nor a member of one"
        introspection.getProperty("password").empty
        introspection.getPropertyMembers("password").isEmpty()

        and: "a package private field with no accessor is not one either"
        introspection.getProperty("note").empty

        and: "a private getter describes what it declares, it does not make a property"
        introspection.getProperty("hidden").empty

        when: "the caller asks for the fields of the type to be properties"
        def fields = ReflectionBeanIntrospection.of(IntroSecrets,
                Set.of(Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD))

        then: "every field is one, whatever its visibility: reflection reaches them and the caller asked for them"
        fields.beanProperties*.name.toSet() == ["name", "note", "password", "hidden"].toSet()
        fields.getRequiredProperty("password", String).get(new IntroSecrets()) == "secret"
    }

    void "a type that asks for field access has its fields as properties"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(IntroFieldAccess)

        expect: "the fields the visibility of the annotation admits, the private one left out as the processor leaves it out"
        introspection.beanProperties*.name.toSet() == ["label", "note"].toSet()
        introspection.getRequiredProperty("label", String).get(new IntroFieldAccess()) == "label"

        and: "and a field with no setter is written through the field itself"
        def bean = new IntroFieldAccess()
        introspection.getRequiredProperty("note", String).set(bean, "written")
        introspection.getRequiredProperty("note", String).get(bean) == "written"
    }

    void "the accessors a property is read and written through are the ones the processor selects"() {
        given: "a type with an overloaded setter and both accessors a boolean is named by"
        def introspections = (1..5).collect { ReflectionBeanIntrospection.of(IntroOverloads) }

        expect: "the setter taking the type of the property writes it, not the overload taking more than it holds"
        introspections.every { introspection ->
            def bean = new IntroOverloads()
            introspection.getRequiredProperty("value", String).set(bean, "written")
            introspection.getRequiredProperty("value", String).get(bean) == "written"
        }

        and: "the overload is a member all the same: the value of the property can be given to its parameter, which is what the processor keeps a setter for"
        def setters = introspections.first().getPropertyMembers("value").findAll {
            it.member instanceof Method && ((Method) it.member).parameterCount == 1
        }
        setters.collect { ((Method) it.member).parameterTypes[0] } as Set == [String, Object] as Set

        and: "isActive reads the boolean, which is the accessor the naming rules generate for it"
        introspections.every { introspection ->
            def bean = new IntroOverloads()
            introspection.getRequiredProperty("active", boolean).set(bean, true)
            introspection.getRequiredProperty("active", boolean).get(bean) == true
        }
    }

    void "a record is described through its components"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(Coordinate)

        expect: "the canonical constructor and a property per component"
        introspection.constructorArguments*.name == ["label", "value"]
        introspection.beanProperties*.name.toSet() == ["label", "value"].toSet()
        introspection.beanReadProperties*.name.toSet() == ["label", "value"].toSet()

        and: "the component is read through the accessor, so an annotation that can only land there is carried"
        def label = introspection.getRequiredProperty("label", String)
        label.stringValue(Coordinate.Axis).get() == "x"
        label.get(new Coordinate("origin", 1)) == "origin"

        and: "the accessor is a member of the property, with the type declaring it"
        introspection.getPropertyMembers("label").any { it.member instanceof java.lang.reflect.Method && it.declaringType == Coordinate }

        and:
        introspection.instantiate("origin", 2) == new Coordinate("origin", 2)
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

    void "a setter taking more than the property holds is the setter of the property"() {
        given: "a type whose only setter takes Object where the property holds a String"
        def introspection = ReflectionBeanIntrospection.of(IntroWidening)
        def bean = new IntroWidening()

        expect: "the property is there, where dropping the setter would have left the type no property at all"
        introspection.propertyNames.toList() == ["value"]

        when:
        introspection.getRequiredProperty("value", Object).set(bean, "written")

        then: "it is written through the setter"
        bean.read() == "written"
    }
}
