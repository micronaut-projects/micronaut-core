package io.micronaut.context

import io.micronaut.inject.BeanIdentifier
import spock.lang.Specification

import java.util.function.Supplier

class BeanDependenciesStateSpec extends Specification {
    void 'closing a registration remains idempotent for a custom bean context'() {
        given:
        def context = Mock(BeanContext)
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def registration = BeanRegistration.of(context, BeanIdentifier.of('custom'), definition, new Object())

        when:
        registration.close()
        registration.close()

        then:
        1 * context.destroyBean(registration)
    }

    void 'retained candidates distinguish unresolved from an authoritative empty selection'() {
        given:
        def dependencies = new BeanDependencies()

        expect:
        dependencies.interceptorCandidates() == InterceptorCandidates.Unresolved.INSTANCE
        dependencies.interceptorRegistrations() == null

        when:
        dependencies.interceptorRegistrations([])

        then:
        dependencies.interceptorCandidates() instanceof InterceptorCandidates.Resolved
        dependencies.interceptorRegistrations().empty
    }

    void 'retained candidates are an immutable snapshot of the supplied registrations'() {
        given:
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def registration = new BeanRegistration(BeanIdentifier.of('advice'), definition, new Object())
        def supplied = [registration]
        def dependencies = new BeanDependencies()

        when:
        dependencies.interceptorRegistrations(supplied)
        supplied.clear()

        then:
        dependencies.interceptorRegistrations() == [registration]

        when:
        dependencies.interceptorRegistrations().clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void 'freezing resolution still permits one destruction claim and one ownership transfer'() {
        given:
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def registration = new BeanRegistration(BeanIdentifier.of('dependent'), definition, new Object())
        def dependencies = new BeanDependencies()
        dependencies.initialize([registration], [])

        when:
        dependencies.stopResolving()
        dependencies.stopResolving()

        then:
        dependencies.isClosing()
        dependencies.beginDestruction()
        !dependencies.beginDestruction()
        dependencies.dependentBeans() == [registration]

        when:
        def transferred = dependencies.takeDependents()
        dependencies.stopResolving()

        then:
        transferred == [registration]
        dependencies.dependentBeans().empty
        dependencies.takeDependents().empty
        !dependencies.beginDestruction()
    }
}
