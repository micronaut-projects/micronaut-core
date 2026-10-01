package io.micronaut.reflection

import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected
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

    // a proxy routes toString, hashCode and equals to the handler too, and Spock renders the proxy when a
    // condition fails: they answer as an object does, so that the failure reported is the real one
    private static final InvocationHandler HANDLER = { Object proxy, java.lang.reflect.Method method, Object[] args ->
        switch (method.name) {
            case "toString": return "proxy"
            case "hashCode": return System.identityHashCode(proxy)
            case "equals": return proxy.is(args[0])
            default: return method.returnType == String ? "proxied" : 42
        }
    }

    // the loader of the interface: a proxy of a package-private interface is defined in the package of it
    private static <T> T proxyOf(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.classLoader, [type] as Class<?>[], HANDLER)
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

    void "a proxy declares no method of its own"() {
        given:
        def introspection = ReflectionBeanIntrospection.of(proxyOf(ProxiedSubContract).getClass())

        expect: "the generated implementations of the proxy class are not the declarations a caller means"
        introspection.findDeclaredMethod("getString").empty
        introspection.findDeclaredMethod("hashCode").empty
    }

    void "the annotations of the interface decide what a property of the proxy is"() {
        given: "an interface naming its own read prefix"
        def proxied = ReflectionBeanIntrospection.of(proxyOf(ProxiedStyled).getClass())

        expect: "the interface itself reports the property its prefix names, and not the one it does not"
        ReflectionBeanIntrospection.of(ProxiedStyled).beanProperties*.name == ["title"]

        and: "so does the proxy, carrying the annotation of the interface"
        proxied.beanProperties*.name == ["title"]
        proxied.annotationMetadata.stringValues(AccessorsStyle, "readPrefixes") == ["read"] as String[]
    }

    void "an accessor the interface declares with @Introspected.Property is an accessor of the proxy"() {
        given:
        def proxied = ReflectionBeanIntrospection.of(proxyOf(ProxiedDeclared).getClass())

        expect: "the interface itself reports the property named after the method"
        ReflectionBeanIntrospection.of(ProxiedDeclared).beanProperties*.name == ["value"]

        and: "so does the proxy, with the annotations of the declaration"
        proxied.beanProperties*.name == ["value"]
        proxied.getRequiredProperty("value", String).annotationMetadata.stringValue(Tag).get() == "declared"
    }

    void "an accessor a super interface declares @Introspected.Property is one of the types inheriting it"() {
        given: "an interface inheriting the declared accessor, a class overriding it, and a proxy of the interface"
        def inherited = ReflectionBeanIntrospection.of(ProxiedSubDeclared)
        def overriding = ReflectionBeanIntrospection.of(ProxiedDeclaredBean)
        def proxied = ReflectionBeanIntrospection.of(proxyOf(ProxiedSubDeclared).getClass())

        expect: "the interface declaring nothing of its own reports the property, as the declaring interface does"
        inherited.beanProperties*.name == ["value"]
        inherited.getRequiredProperty("value", String).annotationMetadata.stringValue(Tag).get() == "declared"

        and: "and a proxy of it, the same way"
        proxied.beanProperties*.name == ["value"]
        proxied.getRequiredProperty("value", String).get(proxyOf(ProxiedSubDeclared)) == "proxied"
        proxied.getRequiredProperty("value", String).members*.declaringType == [ProxiedDeclared]

        and: "but not a class overriding the accessor: the override hides the declaration and carries no annotation of its own"
        overriding.beanProperties.empty
    }

    void "a private method of an interface is not a method of the proxy"() {
        given:
        def bean = proxyOf(ProxiedHelper)
        def proxied = ReflectionBeanIntrospection.of(bean.getClass())

        expect: "the interface reports its public methods alone, the default one included"
        ReflectionBeanIntrospection.of(ProxiedHelper).beanMethods*.name.toSet() == ["getName", "describe"].toSet()

        and: "so does the proxy, which the private method is no method of either"
        proxied.beanMethods*.name.toSet() == ["getName", "describe"].toSet()
    }

    void "a proxy of a package-private interface is served by the fallback under the annotations of the interface"() {
        given: "a configuration describing the package with field access, which the interface itself does not declare"
        def registration = ReflectionIntrospectionPolicy.configure(["io.micronaut.reflection.*"],
            ReflectionAnnotations.declaring(Introspected, ["accessKind": [Introspected.AccessKind.FIELD] as Introspected.AccessKind[]]))
        def proxyClass = proxyOf(ProxiedPackaged).getClass()

        expect: "the proxy class is named after the package of the interface, which the pattern matches"
        proxyClass.name.startsWith("io.micronaut.reflection.")
        ReflectionIntrospectionPolicy.isAllowed(proxyClass)

        when:
        def introspection = new ReflectionBeanIntrospectionFallback().findIntrospection(proxyClass)

        then: "the method access the interface declares wins over the configured field access, as it does for a class"
        introspection.present
        introspection.get().beanProperties*.name == ["name"]

        cleanup:
        registration.close()
        ReflectionIntrospectionPolicy.reset()
    }

    void "a proxy is not built through the builder its interface names"() {
        when:
        def proxied = ReflectionBeanIntrospection.of(proxyOf(ProxiedBuilt).getClass())

        then: "the builder method is a static method of the interface, not of the proxy class"
        !proxied.isBuildable()
        !proxied.hasBuilder()
        proxied.beanProperties*.name == ["name"]
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

    void "an inherited @Introspected.Property accessor is the property the generated introspection reports"() {
        given: "the types inheriting the declared accessor, and the ones overriding it, described both ways"
        def generatedSub = BeanIntrospector.SHARED.getIntrospection(ProxiedParityDeclaredSub)
        def generatedBase = BeanIntrospector.SHARED.getIntrospection(ProxiedParityDeclaredBase)
        def generatedBean = BeanIntrospector.SHARED.getIntrospection(ProxiedParityDeclaredBean)
        def generatedPlain = BeanIntrospector.SHARED.getIntrospection(ProxiedParityDeclaredPlainBean)

        expect: "the generated side reports the property to the types inheriting the accessor"
        generatedSub.beanProperties*.name == ["value"]
        generatedBase.beanProperties*.name == ["value"]

        and: "not to the ones overriding it, whether or not the override says @Override: the annotation is not inherited"
        generatedBean.beanProperties.empty
        generatedPlain.beanProperties.empty

        and: "the reflective side reports the same, through a proxy of the interface too"
        ReflectionBeanIntrospection.of(ProxiedParityDeclaredSub).beanProperties*.name == generatedSub.beanProperties*.name
        ReflectionBeanIntrospection.of(ProxiedParityDeclaredBase).beanProperties*.name == generatedBase.beanProperties*.name
        ReflectionBeanIntrospection.of(ProxiedParityDeclaredBean).beanProperties*.name == generatedBean.beanProperties*.name
        ReflectionBeanIntrospection.of(ProxiedParityDeclaredPlainBean).beanProperties*.name == generatedPlain.beanProperties*.name
        ReflectionBeanIntrospection.of(proxyOf(ProxiedParityDeclaredSub).getClass()).beanProperties*.name == generatedSub.beanProperties*.name
    }
}
