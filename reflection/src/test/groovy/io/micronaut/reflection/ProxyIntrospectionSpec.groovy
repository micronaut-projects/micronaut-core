package io.micronaut.reflection

import io.micronaut.core.beans.BeanIntrospector
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

/**
 * A {@link Proxy} stands for the interfaces it was created with and declares nothing of its own. A
 * specification handed such an instance - Jakarta Validation validating a proxied bean - describes its class,
 * and must read there what those interfaces declare, including what they inherit from the interfaces they
 * extend.
 */
class ProxyIntrospectionSpec extends Specification {

    private static final InvocationHandler HANDLER = { Object proxy, java.lang.reflect.Method method, Object[] args ->
        method.name == "getString" ? "proxied" : 42
    }

    private static <T> T proxyOf(Class<T> type) {
        return (T) Proxy.newProxyInstance(ProxyIntrospectionSpec.classLoader, [type] as Class<?>[], HANDLER)
    }

    void "an interface is described through the accessors it inherits"() {
        given: "an interface declaring nothing of its own"
        def introspection = ReflectionBeanIntrospection.of(ProxiedSubContract)

        expect: "the properties of the interface it extends"
        introspection.beanProperties*.name.toSet() == ["string", "integer"].toSet()

        and: "with the annotations declared on the accessors of that interface"
        introspection.getRequiredProperty("string", String).annotationMetadata.intValue(Sized, "min").asInt == 2
        introspection.getRequiredProperty("integer", Integer).annotationMetadata.stringValue(Tag).get() == "integer"

        and: "and the member of a property is the declaration of the interface declaring it"
        def member = introspection.getRequiredProperty("string", String).members.first()
        member.declaringType == ProxiedContract
        ((ReflectiveIntrospection.ReflectivePropertyMember) member).member == ProxiedContract.getMethod("getString")
    }

    void "a proxy is described through the interfaces it stands for"() {
        given: "a proxy of an interface declaring nothing of its own"
        def bean = proxyOf(ProxiedSubContract)
        def introspection = ReflectionBeanIntrospection.of(bean.getClass())

        expect: "the proxy class is introspectable, though its name is one of the JDK"
        bean.getClass().name.startsWith("jdk.proxy")
        ReflectionBeanIntrospection.isIntrospectable(bean.getClass())

        and: "the properties are the ones the proxied interface inherits"
        introspection.beanProperties*.name.toSet() == ["string", "integer"].toSet()

        and: "carrying the annotations declared on the super interface, not the bare ones of the proxy methods"
        introspection.getRequiredProperty("string", String).annotationMetadata.intValue(Sized, "min").asInt == 2
        introspection.getRequiredProperty("integer", Integer).annotationMetadata.stringValue(Tag).get() == "integer"

        and: "a property is read through the proxy"
        introspection.getRequiredProperty("string", String).get(bean) == "proxied"
        introspection.getRequiredProperty("integer", Integer).get(bean) == 42

        and: "the type declaring a member is the interface, not the synthetic proxy class"
        introspection.getRequiredProperty("string", String).members*.declaringType == [ProxiedContract]
        introspection.beanMethods*.declaringType.toSet() == [ProxiedContract].toSet()

        and: "the constructor a proxy takes its handler through is neither the one it is built by nor listed"
        !introspection.isBuildable()
        introspection.constructorArguments.length == 0
        introspection.constructors*.arguments*.type == [[]]
    }

    void "a proxy of the interface declaring the accessors is described the same way"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(proxyOf(ProxiedContract).getClass())

        expect: "a proxy of the sub interface and one of the interface itself describe the same properties"
        introspection.beanProperties*.name.toSet() == ["string", "integer"].toSet()
        introspection.getRequiredProperty("string", String).annotationMetadata.intValue(Sized, "min").asInt == 2
    }

    void "a proxy is served by the introspector a specification reflects through"() {
        given:
        def introspector = new ReflectionBeanIntrospector(BeanIntrospector.SHARED, { true })

        expect: "the class of a proxied bean yields an introspection, where it yielded none"
        introspector.findIntrospection(proxyOf(ProxiedSubContract).getClass()).get().beanProperties*.name.toSet() ==
            ["string", "integer"].toSet()
    }

    void "a proxy of the interfaces of the JDK alone is not described"() {
        expect: "the interfaces a reflective introspection turns away are turned away through a proxy too"
        !ReflectionBeanIntrospection.isIntrospectable(proxyOf(List).getClass())
        !ReflectionBeanIntrospection.isIntrospectable(proxyOf(Comparable).getClass())
    }

    void "the properties of an inherited accessor are the ones the generated introspection reports"() {
        given: "an @Introspected interface declaring nothing of its own, described both ways"
        def generated = BeanIntrospector.SHARED.getIntrospection(ProxiedParitySub)
        def reflective = ReflectionBeanIntrospection.of(ProxiedParitySub)

        expect: "the generated side reports the properties of the interface it extends"
        generated.beanProperties*.name.toSet() == ["string", "integer"].toSet()
        generated.getRequiredProperty("string", String).annotationMetadata.stringValue(Tag).get() == "contract-string"

        and: "the reflective side reports the same, through a proxy of the interface too"
        reflective.beanProperties*.name.toSet() == generated.beanProperties*.name.toSet()
        reflective.getRequiredProperty("string", String).annotationMetadata.stringValue(Tag).get() ==
            generated.getRequiredProperty("string", String).annotationMetadata.stringValue(Tag).get()
        ReflectionBeanIntrospection.of(proxyOf(ProxiedParitySub).getClass()).beanProperties*.name.toSet() ==
            generated.beanProperties*.name.toSet()
    }
}
