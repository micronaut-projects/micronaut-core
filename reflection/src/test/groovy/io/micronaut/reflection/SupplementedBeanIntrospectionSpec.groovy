package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

@Introspected(classes = [Ledger, IntroWriteOnly])
class SupplementedBeanIntrospectionSpec extends Specification {

    private static <T> SupplementedBeanIntrospection<T> supplemented(Class<T> type) {
        new SupplementedBeanIntrospection<T>(BeanIntrospection.getIntrospection(type), ReflectionBeanIntrospection.of(type))
    }

    void "the type, the metadata and the indexed properties are the ones of the generated introspection"() {
        given:
        def introspection = supplemented(Catalogue)

        expect:
        introspection.beanType == Catalogue
        introspection.generated.is(BeanIntrospection.getIntrospection(Catalogue))
        introspection.annotationMetadata.hasAnnotation(Introspected)
        introspection.toString().startsWith("SupplementedBeanIntrospection(")
    }

    void "a method the processor generated is served as generated, with the class reflection knows declares it"() {
        given:
        def introspection = supplemented(Catalogue)

        when: "the method the processor generated, which an interface declares"
        def described = introspection.beanMethods.find { it.name == "describe" }

        then: "it is the generated method, not a reflective one"
        !(described instanceof ReflectionBeanIntrospection.ReflectionMethod)
        described.annotationMetadata.hasAnnotation(Executable)
        described.invoke(new Catalogue("art", ["a"])) == "catalogue:art"
    }

    void "a method the processor left out comes from reflection"() {
        given:
        def introspection = supplemented(Catalogue)

        expect: "the generated introspection describes only the @Executable method"
        BeanIntrospection.getIntrospection(Catalogue).beanMethods*.name == ["describe"]

        when:
        def names = introspection.beanMethods*.name

        then: "the public method that is not @Executable is there too, once"
        names.contains("describe")
        names.contains("omitted")
        names.count { it == "describe" } == 1

        and:
        introspection.beanMethods.find { it.name == "omitted" }.invoke(new Catalogue("art", [])) == "omitted"
    }

    void "findDeclaredMethod answers from reflection, which the generated introspection cannot"() {
        given:
        def introspection = supplemented(Catalogue)

        expect: "a method the type declares, with the annotations of that declaration only"
        introspection.findDeclaredMethod("omitted").present
        introspection.findDeclaredMethod("describe").present
        introspection.findDeclaredMethod("missing").empty
    }

    void "the generated constructor is served as generated when the processor described it"() {
        given:
        def introspection = supplemented(Catalogue)

        expect: "the processor does record the annotations of a constructor it sees"
        BeanIntrospection.getIntrospection(Catalogue).constructor.annotationMetadata
                .getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["ctor"]

        when:
        def constructor = introspection.constructor

        then: "so the supplemented introspection returns it untouched"
        constructor.is(BeanIntrospection.getIntrospection(Catalogue).constructor)
        constructor.declaringBeanType == Catalogue
        constructor.arguments*.type == [String, List]
        constructor.instantiate("art", ["a"]).name == "art"
    }

    void "a constructor the processor described with no metadata gains the arguments reflection reads"() {
        given: "a constructor carrying no annotation of its own, whose parameter declares a type-use one"
        def generated = BeanIntrospection.getIntrospection(Ledger)
        def introspection = new SupplementedBeanIntrospection<Ledger>(generated, ReflectionBeanIntrospection.of(Ledger))

        expect: "the generated constructor has no metadata and drops the type-use annotation"
        generated.constructor.annotationMetadata.isEmpty()
        generated.constructorArguments[0].typeParameters[0].annotationMetadata
                .getAnnotationValuesByType(Tag).isEmpty()

        when:
        def constructor = introspection.constructor

        then: "the supplemented one merges the arguments reflection read"
        constructor.arguments[0].typeParameters[0].annotationMetadata
                .getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["entry"]

        and: "and still instantiates through the generated introspection"
        constructor.declaringBeanType == Ledger
        constructor.instantiate(["a", "b"]).entries == ["a", "b"]
    }

    void "every constructor of the type is listed, the generated one first"() {
        given:
        def introspection = supplemented(Catalogue)

        expect: "the generated introspection describes one constructor"
        introspection.constructorArguments*.type == [String, List]

        when:
        def constructors = introspection.constructors

        then: "the generated one first, then the one the processor did not select"
        constructors.size() == 2
        constructors[0].arguments*.type == [String, List]
        constructors[1].arguments*.type == [String]
        constructors[1].instantiate("solo").entries == []
    }

    void "a generated property carries the type arguments reflection reads from every member"() {
        given:
        def introspection = supplemented(Catalogue)

        expect: "the generated property has no type-use annotation: the processor read the field"
        BeanIntrospection.getIntrospection(Catalogue).getRequiredProperty("entries", List)
                .asArgument().typeParameters[0].annotationMetadata.getAnnotationValuesByType(Tag).isEmpty()

        when:
        def property = introspection.getProperty("entries").get()

        then: "the supplemented one carries the annotation the interface declares on the type argument"
        property.asArgument().typeParameters[0].annotationMetadata
                .getAnnotationValuesByType(Tag)*.stringValue()*.get() == ["elem"]

        and: "while the property itself is still the generated one, read and written through it"
        property.name == "entries"
        property.type == List
        property.get(new Catalogue("art", ["a", "b"])) == ["a", "b"]
        !property.readOnly
        introspection.getProperty("missing").empty
    }

    void "the merged properties are built once, and every view is built with them"() {
        given:
        def introspection = supplemented(Catalogue)

        when:
        def property = introspection.getProperty("entries").get()

        then: "the lookup by name yields the property the merged view holds, not a description of it built again"
        introspection.getProperty("entries").get().is(property)
        introspection.beanProperties.find { it.name == "entries" }.is(property)

        and: "the read and the write views are built once too, and hold the same properties every time"
        introspection.beanReadProperties.is(introspection.beanReadProperties)
        introspection.beanWriteProperties.is(introspection.beanWriteProperties)

        and: "the views hold the properties a value can be read from and written to, as the merged view reports them"
        introspection.beanReadProperties*.name.toSet() == introspection.beanProperties.findAll { !it.writeOnly }*.name.toSet()
        introspection.beanWriteProperties*.name.toSet() == introspection.beanProperties.findAll { !it.readOnly }*.name.toSet()
    }

    void "a property read and written as different types keeps the arguments the generated introspection reports"() {
        given: "a type read as a List and written as a Collection"
        def generated = BeanIntrospection.getIntrospection(SupTags)
        def introspection = supplemented(SupTags)

        expect: "the processor describes one property, whose read and write arguments are not the same"
        generated.beanProperties*.name == ["tags"]
        generated.beanReadProperties*.type == [List]
        generated.beanWriteProperties*.type == [Collection]

        when:
        def read = introspection.beanReadProperties
        def write = introspection.beanWriteProperties

        then: "the supplemented introspection serves those very properties, not the merged view filtered"
        read*.name == ["tags"]
        write*.name == ["tags"]
        read[0].is(generated.beanReadProperties[0])
        write[0].is(generated.beanWriteProperties[0])

        and: "so the argument of the accessor behind each of them is the one it reports"
        read[0].type == List
        write[0].type == Collection
        read[0].get(new SupTags(tags: ["a"])) == ["a"]

        and: "while the merged view is the one property the processor describes"
        introspection.beanProperties*.name == ["tags"]
        introspection.getProperty("tags").get().is(introspection.beanProperties[0])
    }

    void "a property only the reflective introspection knows is in no view of the properties"() {
        given: "a reflective introspection asked for field access, which makes a property of a bare field"
        def generated = BeanIntrospection.getIntrospection(SupNotes)
        def reflected = ReflectionBeanIntrospection.of(SupNotes,
                Set.of(Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD))
        def introspection = new SupplementedBeanIntrospection<SupNotes>(generated, reflected)

        expect: "the processor describes the property of the accessors alone"
        generated.beanProperties*.name == ["title"]
        reflected.beanProperties*.name.toSet() == ["title", "note"].toSet()

        when:
        def read = introspection.beanReadProperties
        def write = introspection.beanWriteProperties

        then: "the properties are the ones the processor describes, which is what the merged view holds too"
        read*.name == ["title"]
        write*.name == ["title"]
        introspection.beanProperties*.name == ["title"]
        introspection.getProperty("note").empty

        and: "and they are the generated properties themselves, each carrying the argument of its own accessor"
        read[0].is(generated.beanReadProperties[0])
        write[0].is(generated.beanWriteProperties[0])
    }

    void "a generated introspection that reads nothing exposes nothing to read"() {
        given: "a type written to and never read from"
        def generated = BeanIntrospection.getIntrospection(IntroWriteOnly)
        def introspection = supplemented(IntroWriteOnly)

        expect: "the processor describes one write property and no read one: the field is private"
        generated.beanReadProperties.isEmpty()
        generated.beanWriteProperties*.name == ["secret"]

        and: "where reflection does read the value, through the field the setter names"
        ReflectionBeanIntrospection.of(IntroWriteOnly).beanReadProperties*.name == ["secret"]

        and: "which does not stand in for the read properties the generated introspection legitimately has none of"
        introspection.beanReadProperties.isEmpty()
        introspection.beanWriteProperties*.name == ["secret"]

        and: "the write property being the one the generated introspection reports"
        introspection.beanWriteProperties[0].is(generated.beanWriteProperties[0])
    }

    void "the members a property is made of come from reflection"() {
        given:
        def introspection = supplemented(Catalogue)

        when:
        def members = introspection.getPropertyMembers("name")

        then: "the field and its accessors, each with the class declaring it"
        members*.declaringType.every { it == Catalogue }
        members.any { it.member instanceof java.lang.reflect.Field }
        members.find { it.member instanceof java.lang.reflect.Field }.read(new Catalogue("art", [])) == "art"

        and:
        introspection.getPropertyMembers("missing").isEmpty()
    }

    void "instantiation and building are delegated to the generated introspection"() {
        given:
        def introspection = supplemented(Catalogue)

        expect:
        introspection.isBuildable() == BeanIntrospection.getIntrospection(Catalogue).isBuildable()
        introspection.hasBuilder() == BeanIntrospection.getIntrospection(Catalogue).hasBuilder()
        introspection.instantiate(false, "art", ["a"]).name == "art"
    }

    void "the reflective introspector supplements a generated introspection when it is asked to"() {
        given:
        def plain = new ReflectionBeanIntrospector(BeanIntrospector.SHARED)
        def supplementing = new ReflectionBeanIntrospector(BeanIntrospector.SHARED, { true }, true)

        expect: "without supplementing, a generated introspection is served untouched"
        !(plain.findIntrospection(Catalogue).get() instanceof SupplementedBeanIntrospection)

        and: "with it, the generated introspection is completed by the reflective one"
        supplementing.findIntrospection(Catalogue).get() instanceof SupplementedBeanIntrospection
        supplementing.findIntrospection(Catalogue).get().beanMethods*.name.contains("omitted")

        and: "a type the processor never saw is reflective either way, not supplemented"
        supplementing.findIntrospection(Book).get() instanceof ReflectionBeanIntrospection
        !(supplementing.findIntrospection(Book).get() instanceof SupplementedBeanIntrospection)
    }

    @Introspected
    static class Catalogue implements Catalogued {

        @Tag("name")
        String name

        List<String> entries

        @Tag("ctor")
        Catalogue(String name, List<String> entries) {
            this.name = name
            this.entries = entries
        }

        protected Catalogue(String name) {
            this(name, [])
        }

        @Executable
        String describe() {
            "catalogue:$name"
        }

        String omitted() {
            "omitted"
        }
    }
}

/**
 * The generated introspections of the Java fixtures of this spec: they are compiled without the processor,
 * so a class of the Groovy sources asks for their introspections the way the module's specs do.
 */
@Introspected(classes = [SupTags, SupNotes])
class SupplementedFixtures {
}
