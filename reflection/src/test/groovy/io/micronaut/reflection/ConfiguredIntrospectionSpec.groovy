package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

/**
 * The {@link Introspected} members an application configuration supplies for the types it allows to be
 * described reflectively: a type of a library, or one compiled without the processor, carries no annotation of
 * its own and has no other way to say how it is to be described.
 */
class ConfiguredIntrospectionSpec extends Specification {

    void cleanup() {
        ReflectionIntrospectionPolicy.reset()
    }

    private static Map<String, Object> allowing(String pattern, Map<String, Object> members = [:]) {
        def entry = ["types": pattern] + members
        return ["micronaut.introspection.reflective": [entry]]
    }

    void "a configured pattern allows the types it matches, as allow-reflection does"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*"))

        when:
        def introspection = BeanIntrospector.SHARED.findIntrospection(ConfiguredBean)

        then:
        introspection.isPresent()
        introspection.get().beanProperties*.name.toSorted() == ["kept", "password", "secret"]

        cleanup:
        context.close()
    }

    void "the described types are no longer described once the context that configured them closes"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*"))

        when:
        context.close()

        then:
        BeanIntrospector.SHARED.findIntrospection(ConfiguredBean).empty
    }

    void "a configured excludes leaves the properties out"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*", ["excludes": ["password", "secret"]]))

        expect:
        BeanIntrospector.SHARED.getIntrospection(ConfiguredBean).beanProperties*.name == ["kept"]

        cleanup:
        context.close()
    }

    void "a configured includes describes those properties alone"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*", ["includes": ["kept"]]))

        expect:
        BeanIntrospector.SHARED.getIntrospection(ConfiguredBean).beanProperties*.name == ["kept"]

        cleanup:
        context.close()
    }

    void "a configured excluded-annotations leaves out the properties carrying one"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*",
            ["excluded-annotations": [Hidden.name]]))

        expect:
        BeanIntrospector.SHARED.getIntrospection(ConfiguredBean).beanProperties*.name.toSorted() == ["kept", "password"]

        cleanup:
        context.close()
    }

    void "a member the configuration does not offer is not bound, and leaves the description as it was"() {
        given: "included-annotations is not offered: the processor applies it to package scanning, not to properties"
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*",
            ["included-annotations": [Hidden.name]]))

        expect:
        BeanIntrospector.SHARED.getIntrospection(ConfiguredBean).beanProperties*.name.toSorted() ==
            ["kept", "password", "secret"]

        cleanup:
        context.close()
    }

    void "a configured access-kind and visibility reach the members they name"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*",
            ["access-kind": ["FIELD"], "visibility": ["ANY"]]))

        expect: "the private field of the type is a property of its own, which METHOD access does not reach"
        BeanIntrospector.SHARED.getIntrospection(ConfiguredBean).beanProperties*.name.contains("tucked")

        cleanup:
        context.close()
    }

    void "a configured annotation-metadata of false leaves the properties without their annotations"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*",
            ["annotation-metadata": false]))

        when:
        def introspection = BeanIntrospector.SHARED.getIntrospection(ConfiguredBean)

        then: "the properties are all there, and carry nothing"
        introspection.beanProperties*.name.toSorted() == ["kept", "password", "secret"]
        introspection.getRequiredProperty("secret", String).annotationMetadata == AnnotationMetadata.EMPTY_METADATA

        cleanup:
        context.close()
    }

    void "a configured indexed makes the index the properties are looked up in"() {
        given:
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.Configured*",
            ["indexed": [["annotation": Hidden.name, "member": "value"]]]))

        when:
        def introspection = BeanIntrospector.SHARED.getIntrospection(ConfiguredBean)

        then:
        introspection.getIndexedProperties(Hidden)*.name == ["secret"]
        introspection.getIndexedProperty(Hidden, "kept-out").map { it.name }.orElse(null) == "secret"
        introspection.getIndexedProperty(Hidden, "absent").empty
    }

    void "a type carrying Introspected of its own keeps what it declares, and takes what the configuration adds"() {
        given: "a type declaring excludes, configured with an excludes of its own and an access kind"
        def context = ApplicationContext.run(allowing("io.micronaut.reflection.SelfDescribed",
            ["excludes": ["kept"], "access-kind": ["FIELD"], "visibility": ["ANY"]]))

        when:
        def introspection = BeanIntrospector.SHARED.getIntrospection(SelfDescribed)

        then: "the excludes the type declares wins, so 'kept' is described and 'password' is not"
        introspection.beanProperties*.name.contains("kept")
        !introspection.beanProperties*.name.contains("password")

        and: "and the access kind the configuration adds applies, as the type declares none"
        introspection.beanProperties*.name.contains("tucked")

        cleanup:
        context.close()
    }
}
