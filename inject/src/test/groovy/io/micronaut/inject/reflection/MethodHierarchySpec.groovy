package io.micronaut.inject.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MethodHierarchySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(["spec.name": "MethodHierarchySpec"])

    @Shared
    BeanIntrospector reflective = new ReflectionBeanIntrospector(BeanIntrospector.SHARED)

    private static List<String> tags(metadata) {
        metadata.getAnnotationValuesByType(Tag).collect { it.stringValue().get() }
    }

    void "a method of a reflective hierarchy reports the declaration of every level, the nearest first"() {
        when:
        def hierarchy = MethodHierarchy.resolve(reflective, Reservation, "reserve", String)

        then: "the super classes first, then the interfaces of each level"
        hierarchy.local().declaringType() == Reservation
        hierarchy.inherited()*.declaringType() == [AbstractReservation, Reservable, Auditable]

        and: "every declaration is exact: reflection reads the annotations of that method alone"
        hierarchy.local().exact()
        hierarchy.inherited().every { it.exact() }
        tags(hierarchy.local().annotationMetadata()) == ["reservation"]
        hierarchy.inherited().collect { tags(it.annotationMetadata()) } == [["abstract"], ["reservable"], ["auditable"]]

        and: "the declaring type declares the method itself, so that is the exact declaration"
        hierarchy.declared().is(hierarchy.local()) || hierarchy.declared().declaringType() == Reservation
        hierarchy.declared().exact()
    }

    void "the merged views hold every level, the local declaration winning"() {
        when:
        def hierarchy = MethodHierarchy.resolve(reflective, Reservation, "reserve", String)

        then: "the method annotations of the whole hierarchy, the farthest first"
        tags(hierarchy.annotationMetadata()) as Set == ["reservable", "auditable", "abstract", "reservation"] as Set

        and: "the parameter annotations of the whole hierarchy"
        hierarchy.arguments().length == 1
        tags(hierarchy.arguments()[0].getAnnotationMetadata()) as Set ==
                ["reservable-param", "auditable-param", "abstract-param", "reservation-param"] as Set

        and: "the type-use annotations of the return type arguments, merged level by level"
        hierarchy.returnArgument().type == List
        tags(hierarchy.returnArgument().typeParameters[0].getAnnotationMetadata()) as Set ==
                ["reservable-item", "auditable-item", "abstract-item", "reservation-item"] as Set
    }

    void "a type declaring the method in more than one branch is parallel"() {
        expect:
        MethodHierarchy.resolve(reflective, Reservation, "reserve", String).parallel()
        !MethodHierarchy.resolve(reflective, AbstractReservation, "reserve", String).parallel()
    }

    void "a type that inherits a method without overriding it reads the declaration of the type declaring it"() {
        when: "the type declares nothing of its own"
        def hierarchy = MethodHierarchy.resolve(reflective, InheritedReservation, "reserve", String)

        then: "the local declaration is the one of the super class actually declaring the method"
        hierarchy.local().declaringType() == AbstractReservation
        tags(hierarchy.local().annotationMetadata()) == ["abstract"]

        and: "and the walk starts there: the interface the sub type does not implement is not reported"
        hierarchy.inherited()*.declaringType() == [Reservable]
    }

    void "an interface reached by two branches is reported once"() {
        when:
        def hierarchy = MethodHierarchy.resolve(reflective, Diamond.Both, "reserve", String)

        then:
        hierarchy.local().declaringType() == Diamond.Both
        hierarchy.inherited()*.declaringType() == [Reservable]
        !hierarchy.parallel()
    }

    void "a method a class inherits as an interface default is found on the interface declaring it"() {
        when:
        def hierarchy = MethodHierarchy.resolve(reflective, Defaulting, "label", new Class[0])

        then:
        hierarchy.local().declaringType() == Labelled
        tags(hierarchy.local().annotationMetadata()) == ["labelled"]
        hierarchy.inherited().isEmpty()
    }

    void "a hierarchy of an interface only reports what the interface declares"() {
        when:
        def hierarchy = MethodHierarchy.resolve(reflective, Reservable, "reserve", String)

        then:
        hierarchy.local().declaringType() == Reservable
        hierarchy.inherited().isEmpty()
        hierarchy.annotationMetadata().is(hierarchy.local().annotationMetadata())
        hierarchy.arguments().is(hierarchy.local().arguments())
    }

    void "a method named by a java.lang.reflect handle resolves against the type declaring it"() {
        given:
        def method = Reservation.getMethod("reserve", String)

        expect:
        MethodHierarchy.resolve(reflective, method).inherited()*.declaringType() ==
                [AbstractReservation, Reservable, Auditable]
    }

    void "a type with no such method is rejected"() {
        when:
        MethodHierarchy.resolve(reflective, Reservation, "cancel", String)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("No method cancel")
        e.message.contains(Reservation.name)
    }

    void "a declaration of a generated introspection is not exact, so the declared view falls back to the local one"() {
        given: "the shared introspector, which serves generated introspections only"
        def introspection = BeanIntrospection.getIntrospection(GeneratedChild)
        def beanMethod = introspection.beanMethods.find { it.name == "describe" }

        when:
        def hierarchy = MethodHierarchy.resolve(BeanIntrospector.SHARED,
                MethodHierarchy.Declaration.of(beanMethod, false), "describe")

        then: "a generated introspection does not tell the annotations of a declaration apart from the ones \
it merges from the methods it overrides, so the declaration is not exact and the declared view falls back"
        !hierarchy.local().exact()
        hierarchy.declared().is(hierarchy.local())

        and: "the parent is introspected too, so its declaration is read as its own level"
        hierarchy.inherited()*.declaringType() == [GeneratedParent]
        !hierarchy.inherited()[0].exact()

        and: "the merged view holds both levels all the same"
        tags(hierarchy.annotationMetadata()) as Set == ["parent", "child"] as Set
    }

    void "a generated introspection completed reflectively tells the declarations apart"() {
        given: "the same types, introspected reflectively on top of the generated introspections"
        def supplementing = new ReflectionBeanIntrospector(BeanIntrospector.SHARED, { true }, true)

        when:
        def hierarchy = MethodHierarchy.resolve(supplementing, GeneratedChild, "describe", String)

        then: "the child declares only its own annotation"
        hierarchy.declared().exact()
        tags(hierarchy.declared().annotationMetadata()) == ["child"]

        and: "the parent declaration is a level of its own"
        hierarchy.inherited()*.declaringType() == [GeneratedParent]
        tags(hierarchy.inherited()[0].annotationMetadata()) == ["parent"]
    }

    void "a method of a bean definition resolves through its executable method"() {
        given:
        def executable = context.getExecutableMethod(CountingChild, "count", String)

        when:
        def hierarchy = MethodHierarchy.resolve(reflective,
                MethodHierarchy.Declaration.of(executable), executable.methodName)

        then: "the annotations of the class are not read as annotations of the method"
        hierarchy.local().declaringType() == CountingChild
        tags(hierarchy.local().annotationMetadata()) == ["counting-child"]

        and:
        hierarchy.inherited()*.declaringType() == [CountingParent]
        tags(hierarchy.annotationMetadata()) as Set == ["counting-parent", "counting-child"] as Set
    }

    void "the metadata of an executable method is read without the metadata of its class"() {
        given:
        def executable = context.getExecutableMethod(CountingChild, "count", String)

        expect: "the executable method carries both, the declaration only the method annotations"
        executable.annotationMetadata.hasAnnotation(Singleton)
        !MethodHierarchy.Declaration.declaredOf(executable.annotationMetadata).hasAnnotation(Singleton)
        tags(MethodHierarchy.Declaration.declaredOf(executable.annotationMetadata)) == ["counting-child"]
    }

    void "a type with no introspection declares nothing"() {
        expect:
        MethodHierarchy.declaredBy(BeanIntrospector.SHARED, Reservation, "reserve", String).isEmpty()
        MethodHierarchy.declaredBy(reflective, Reservation, "reserve", String).isPresent()
    }

    void "merging one level returns that level as it is"() {
        given:
        def only = BeanIntrospection.getIntrospection(GeneratedParent).beanMethods[0].annotationMetadata

        expect:
        MethodHierarchy.mergeMetadata([]).isEmpty()
        MethodHierarchy.mergeMetadata([only]).is(only)
    }

    interface Labelled {
        @Tag("labelled")
        default String label() {
            "labelled"
        }
    }

    static class Defaulting implements Labelled {
    }

    @Introspected
    static class GeneratedParent {
        @Executable
        @Tag("parent")
        String describe(String value) {
            "parent:$value"
        }
    }

    @Introspected
    static class GeneratedChild extends GeneratedParent {
        @Override
        @Executable
        @Tag("child")
        String describe(String value) {
            "child:$value"
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "MethodHierarchySpec")
    static class CountingParent {
        @Executable
        @Tag("counting-parent")
        int count(String value) {
            value.length()
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "MethodHierarchySpec")
    static class CountingChild extends CountingParent {
        @Override
        @Executable
        @Tag("counting-child")
        int count(String value) {
            super.count(value) * 2
        }
    }
}
