package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

class ReflectionIntrospectionPolicySpec extends Specification {

    def cleanup() {
        ReflectionIntrospectionPolicy.reset()
    }

    void "no type is described reflectively by default"() {
        expect:
        !ReflectionIntrospectionPolicy.isAllowed(Allowed)
        BeanIntrospector.SHARED.findIntrospection(Allowed).empty
        BeanIntrospector.forClassLoader(getClass().classLoader).findIntrospection(Allowed).empty
    }

    void "a package pattern allows the package and its sub packages"() {
        when:
        ReflectionIntrospectionPolicy.allow("io.micronaut.reflection.*")

        then:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        !ReflectionIntrospectionPolicy.isAllowed(String)

        when: "the shared introspector describes the type"
        def introspection = BeanIntrospector.SHARED.findIntrospection(Allowed).get()

        then:
        introspection instanceof ReflectionBeanIntrospection
        introspection.getRequiredProperty("title", String).name == "title"
        BeanIntrospection.getIntrospection(Allowed).is(introspection)
        BeanIntrospector.forClassLoader(getClass().classLoader).findIntrospection(Allowed).get().is(introspection)

        and: "a type with a generated introspection is still served generated"
        !(BeanIntrospector.SHARED.findIntrospection(Generated).get() instanceof ReflectionBeanIntrospection)
    }

    void "an exact name allows one class"() {
        when:
        ReflectionIntrospectionPolicy.allow("io.micronaut.reflection.Allowed")

        then:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        !ReflectionIntrospectionPolicy.isAllowed(Warehouse)
    }

    void "a star allows every class, but the types of the JDK are never described"() {
        when:
        ReflectionIntrospectionPolicy.allow("*")

        then:
        ReflectionIntrospectionPolicy.isAllowed(Warehouse)
        ReflectionIntrospectionPolicy.isAllowed(String)
        BeanIntrospector.SHARED.findIntrospection(Warehouse).present
        BeanIntrospector.SHARED.findIntrospection(String).empty
    }

    void "the application configuration allows types while the context runs"() {
        given:
        def context = ApplicationContext.run('micronaut.introspection.allow-reflection': ['io.micronaut.reflection.Allowed'])

        expect:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        !ReflectionIntrospectionPolicy.isAllowed(Warehouse)
        context.getBean(ReflectionIntrospectionConfiguration).allowReflection == ['io.micronaut.reflection.Allowed']

        when:
        context.close()

        then:
        !ReflectionIntrospectionPolicy.isAllowed(Allowed)
    }

    void "a predicate allows types programmatically"() {
        when:
        ReflectionIntrospectionPolicy.allow { Class<?> type -> type == Warehouse }

        then:
        ReflectionIntrospectionPolicy.isAllowed(Warehouse)
        !ReflectionIntrospectionPolicy.isAllowed(Allowed)

        when:
        ReflectionIntrospectionPolicy.reset()

        then:
        !ReflectionIntrospectionPolicy.isAllowed(Warehouse)
    }

    @Introspected
    static class Generated {
        String name
    }
}
