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

    void 'destruction candidates adapt legacy attributes only when no typed selection exists'() {
        given:
        def context = new DefaultBeanContext()
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def registration = new BeanRegistration(BeanIdentifier.of('legacy'), definition, new Object())
        def resolution = new DefaultBeanResolutionContext(context, definition)
        resolution.setAttribute(BeanResolutionContext.EXISTING_INTERCEPTOR_REGISTRATIONS, [registration])

        expect:
        resolution.getBeanDestructionInterceptors(definition) == [registration]

        when:
        resolution.setBeanInterceptors(definition, [])

        then:
        resolution.getBeanDestructionInterceptors(definition).empty
        resolution.getBeanInterceptors(definition).empty

        cleanup:
        resolution.close()
        context.close()
    }

    void 'retained candidates distinguish unresolved from an authoritative empty selection'() {
        given:
        def dependencies = new BeanDependencies()

        expect:
        dependencies.interceptorCandidates() == InterceptorCandidates.Unresolved.INSTANCE
        dependencies.interceptorRegistrations() == null

        when:
        dependencies.retainInterceptorCandidates([])

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
        dependencies.retainInterceptorCandidates(supplied)
        supplied.clear()

        then:
        dependencies.interceptorRegistrations() == [registration]

        when:
        dependencies.interceptorRegistrations().clear()

        then:
        thrown(UnsupportedOperationException)
    }

    void 'creation carries unresolved and resolved-empty candidates without rediscovery'() {
        given:
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def creation = new BeanCreationState(definition, [])

        expect:
        creation.lifecycleInterceptorCandidates() == InterceptorCandidates.Unresolved.INSTANCE

        when:
        creation.dependencies.retainInterceptorCandidates([])

        then:
        def candidates = creation.lifecycleInterceptorCandidates()
        candidates instanceof InterceptorCandidates.Resolved
        candidates.registrations().empty
        candidates.is(creation.dependencies.interceptorCandidates())
    }

    void 'destruction candidates prefer proxy advice while initialization retains target advice'() {
        given:
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def otherDefinition = RuntimeBeanDefinition.builder(String, { 'other' } as Supplier<String>).build()
        def proxyAdvice = new BeanRegistration(BeanIdentifier.of('proxy'), definition, new Object())
        def targetAdvice = new BeanRegistration(BeanIdentifier.of('target'), definition, new Object())
        def otherAdvice = new BeanRegistration(BeanIdentifier.of('other'), otherDefinition, 'other')
        def creation = new BeanCreationState(definition, [proxyAdvice])
        creation.dependencies.retainInterceptorCandidates([targetAdvice, otherAdvice])

        expect:
        creation.lifecycleInterceptorCandidates().registrations() == [proxyAdvice, otherAdvice]
        creation.dependencies.interceptorCandidates().registrations() == [targetAdvice, otherAdvice]
    }

    void 'freezing resolution still permits one destruction claim and one ownership transfer'() {
        given:
        def definition = RuntimeBeanDefinition.builder(Object, { new Object() } as Supplier<Object>).build()
        def registration = new BeanRegistration(BeanIdentifier.of('dependent'), definition, new Object())
        def dependencies = new BeanDependencies()
        dependencies.initialize([registration], new InterceptorCandidates.Resolved([]))

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
