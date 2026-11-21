package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanTypeHierarchy
import io.micronaut.inject.visitor.beans.hierarchy.BoundRepo
import spock.lang.Specification

class BeanTypeHierarchyDefaultSignatureSpec extends Specification {

    void "the default signature lookup matches the bean methods of the shared introspection"() {
        given: 'a hierarchy implementing only the abstract methods, so the signature lookups take the defaults'
        def generated = BeanIntrospection.getIntrospection(BoundRepo).typeHierarchy.orElseThrow()
        def hierarchy = new Delegating(generated)

        expect:
        hierarchy.getDeclaringTypes('put', String, Integer) == generated.getDeclaringTypes('put', String, Integer)
        !hierarchy.getDeclaringTypes('put', String, Integer).isEmpty()
        hierarchy.isDeclared('put', String, Integer)
        hierarchy.getDeclaringTypes('put', Object, Object).isEmpty()
        hierarchy.getDeclaringTypes('missing').isEmpty()
        !hierarchy.isDeclared('missing')
    }

    void "the default signature lookup answers empty for a type without an introspection"() {
        given:
        def hierarchy = new Delegating(BeanIntrospection.getIntrospection(BoundRepo).typeHierarchy.orElseThrow(), Thread)

        expect:
        hierarchy.getDeclaringTypes('run').isEmpty()
        !hierarchy.isDeclared('run')
    }

    static class Delegating implements BeanTypeHierarchy {
        private final BeanTypeHierarchy delegate
        private final Class<?> beanType

        Delegating(BeanTypeHierarchy delegate, Class<?> beanType = null) {
            this.delegate = delegate
            this.beanType = beanType ?: delegate.beanType
        }

        @Override
        Class<?> getBeanType() { beanType }

        @Override
        List<Class<?>> getTypes() { delegate.types }

        @Override
        Optional<Class<?>> getSuperclass(Class<?> type) { delegate.getSuperclass(type) }

        @Override
        List<Class<?>> getInterfaces(Class<?> type) { delegate.getInterfaces(type) }

        @Override
        List<Class<?>> getDeclaringTypes(BeanMethod<?, ?> method) { delegate.getDeclaringTypes(method) }

        @Override
        List<BeanMethod<?, ?>> getDeclaredMethods() { delegate.declaredMethods }
    }
}
