package io.micronaut.reflection

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionFallback
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.order.Ordered
import spock.lang.Specification

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Predicate

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

    void "a pattern for a package covers a class of a sub package"() {
        when:
        ReflectionIntrospectionPolicy.allow("java.util.*")

        then: "the star crosses the package separator, which is what a package and its sub packages means"
        ReflectionIntrospectionPolicy.isAllowed(List)
        ReflectionIntrospectionPolicy.isAllowed(TimeUnit)
        !ReflectionIntrospectionPolicy.isAllowed(String)
    }

    void "a pattern matches the whole class name"() {
        when:
        ReflectionIntrospectionPolicy.allow(pattern)

        then:
        ReflectionIntrospectionPolicy.isAllowed(Allowed) == allows

        where:
        pattern                             || allows
        'io.micronaut.reflection.Allowed'   || true
        'io.micronaut.reflection.*'         || true
        '*.Allowed'                         || true
        '*Allowed'                          || true
        '*'                                 || true
        'io.micronaut.refl.*'               || false
        'micronaut.reflection.*'            || false
        'io.micronaut.reflection.Allow'     || false
        'io.micronaut.reflection.Allowed.*' || false
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

    void "two contexts allow their own types without taking away each other's"() {
        given:
        def first = ApplicationContext.run('micronaut.introspection.allow-reflection': ['io.micronaut.reflection.Allowed'])
        def second = ApplicationContext.run('micronaut.introspection.allow-reflection': ['io.micronaut.reflection.Warehouse'])

        expect: "what applies is the union of the contexts that are running"
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        ReflectionIntrospectionPolicy.isAllowed(Warehouse)

        when:
        second.close()

        then: "closing one context leaves the patterns of the other"
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        !ReflectionIntrospectionPolicy.isAllowed(Warehouse)

        when:
        first.close()

        then:
        !ReflectionIntrospectionPolicy.isAllowed(Allowed)

        cleanup:
        first.close()
        second.close()
    }

    void "a configuration takes back exactly the patterns it contributed"() {
        given: "two configurations contribute the way the configuration bean does"
        def first = ReflectionIntrospectionPolicy.configure(['io.micronaut.reflection.Allowed'])
        def second = ReflectionIntrospectionPolicy.configure(['io.micronaut.reflection.Warehouse'])

        expect:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        ReflectionIntrospectionPolicy.isAllowed(Warehouse)

        when:
        second.close()

        then:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)
        !ReflectionIntrospectionPolicy.isAllowed(Warehouse)

        when: "closing a registration again does nothing"
        second.close()

        then:
        ReflectionIntrospectionPolicy.isAllowed(Allowed)

        when:
        first.close()

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

    void "concurrent allow calls do not lose a predicate"() {
        given:
        def calls = new AtomicInteger()
        def threads = (1..64).collect {
            Thread.start {
                ReflectionIntrospectionPolicy.allow({ Class<?> type -> calls.incrementAndGet(); false } as Predicate)
            }
        }

        when:
        threads*.join()
        def allowed = ReflectionIntrospectionPolicy.isAllowed(Allowed)

        then: "one check consults every predicate, so no update was lost"
        !allowed
        calls.get() == 64
    }

    void "a type the fallback did not serve is served once it is allowed"() {
        expect: "a miss is not remembered as a refusal, the policy is asked on every lookup"
        BeanIntrospector.SHARED.findIntrospection(Allowed).empty
        BeanIntrospector.SHARED.findIntrospection(Allowed).empty

        when:
        ReflectionIntrospectionPolicy.allow("io.micronaut.reflection.Allowed")

        then:
        BeanIntrospector.SHARED.findIntrospection(Allowed).present

        and: "the description of the class is cached, the same introspection is served again"
        BeanIntrospector.SHARED.findIntrospection(Allowed).get()
                .is(BeanIntrospector.SHARED.findIntrospection(Allowed).get())

        when:
        ReflectionIntrospectionPolicy.allow("*")

        then: "a type the fallback cannot describe stays a miss however often it is asked for"
        BeanIntrospector.SHARED.findIntrospection(String).empty
        BeanIntrospector.SHARED.findIntrospection(String).empty
    }

    void "a fallback that fails leaves the lookup a miss"() {
        given: "a class loader registering a fallback that throws, ordered before the reflective one"
        def services = Files.createTempDirectory("fallbacks").toFile()
        def descriptors = new File(services, "META-INF/services")
        descriptors.mkdirs()
        new File(descriptors, BeanIntrospectionFallback.name).text = Failing.name
        def classLoader = new URLClassLoader([services.toURI().toURL()] as URL[], getClass().classLoader)
        def introspector = BeanIntrospector.forClassLoader(classLoader)

        expect: "the failure of one fallback is a miss, not a thrown IntrospectionException"
        introspector.findIntrospection(Warehouse).empty

        when: "the type is allowed, so the fallback ordered after the failing one serves it"
        ReflectionIntrospectionPolicy.allow("io.micronaut.reflection.Warehouse")

        then:
        introspector.findIntrospection(Warehouse).get() instanceof ReflectionBeanIntrospection

        cleanup:
        classLoader.close()
        services.deleteDir()
    }

    @Introspected
    static class Generated {
        String name
    }

    /**
     * A fallback that fails the way one describing a class with a member of an absent dependency fails.
     */
    static class Failing implements BeanIntrospectionFallback {

        @Override
        public <T> Optional<BeanIntrospection<T>> findIntrospection(Class<T> beanType) {
            throw new NoClassDefFoundError("io/example/Absent")
        }

        @Override
        int getOrder() {
            Ordered.HIGHEST_PRECEDENCE
        }
    }

    void "two contributions of the same patterns are withdrawn one by one"() {
        given: "two configurations allowing everything, which compiles to the one predicate the JVM caches"
        def first = ReflectionIntrospectionPolicy.configure(["*"])
        def second = ReflectionIntrospectionPolicy.configure(["*"])

        when: "one of them is withdrawn"
        first.close()

        then: "what the other one contributed still applies"
        ReflectionIntrospectionPolicy.isAllowed(Book)

        when:
        second.close()

        then:
        !ReflectionIntrospectionPolicy.isAllowed(Book)

        cleanup:
        ReflectionIntrospectionPolicy.reset()
    }

    void "the fallback never describes a type of the platform"() {
        given:
        def registration = ReflectionIntrospectionPolicy.configure(["*"])
        def fallback = new ReflectionBeanIntrospectionFallback()

        expect: "a type of the platform is not served, whatever the patterns allow"
        fallback.findIntrospection(Thread).empty
        fallback.findIntrospection(StringBuilder).empty

        and: "a type of the application is"
        fallback.findIntrospection(Book).present

        cleanup:
        registration.close()
        ReflectionIntrospectionPolicy.reset()
    }
}
