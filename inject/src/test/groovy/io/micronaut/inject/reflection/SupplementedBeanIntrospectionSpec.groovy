package io.micronaut.inject.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

@Introspected(classes = [Ledger])
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
