package io.micronaut.context

import io.micronaut.aop.beandefinition.SharedInterceptorRegistrations
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class SharedInterceptorCompatibilitySpec extends Specification {

    void 'legacy interceptor registrations remain visible inside a creation frame'() {
        given:
        def context = new DefaultBeanContext()
        BeanDefinition<?> definition = Stub()
        def resolution = new DefaultBeanResolutionContext(context, definition)
        def legacy = [Stub(BeanRegistration)]
        def current = [Stub(BeanRegistration)]
        resolution.beginCreation(definition)
        resolution.setBeanInterceptors(definition, current)

        when:
        SharedInterceptorRegistrations.store(resolution, definition, legacy)

        then:
        resolution.getBeanInterceptors(definition).is(legacy)

        when: 'legacy callers ignore absent or empty candidates'
        SharedInterceptorRegistrations.store(resolution, definition, null)
        SharedInterceptorRegistrations.store(resolution, definition, [])

        then:
        resolution.getBeanInterceptors(definition).is(legacy)

        when: 'the legacy entry is removed'
        resolution.removeAttribute(BeanResolutionContext.INTERCEPTOR_REGISTRATIONS)

        then:
        resolution.getBeanInterceptors(definition).is(current)

        cleanup:
        resolution.close()
        context.close()
    }

    void 'legacy construction stack remains isolated and stores completed candidates'() {
        given:
        def context = new DefaultBeanContext()
        BeanDefinition<?> outer = Stub()
        BeanDefinition<?> inner = Stub()
        def resolution = new DefaultBeanResolutionContext(context, outer)
        def outerCandidates = [Stub(BeanRegistration)]
        def innerCandidates = [Stub(BeanRegistration)]
        resolution.beginCreation(outer)

        when:
        SharedInterceptorRegistrations.push(resolution, outer, outerCandidates)
        SharedInterceptorRegistrations.push(resolution, inner, innerCandidates)
        SharedInterceptorRegistrations.pop(resolution, outer, outerCandidates)

        then: 'an unmatched pop does not remove the nested entry'
        SharedInterceptorRegistrations.peek(resolution, inner).is(innerCandidates)

        when:
        SharedInterceptorRegistrations.pop(resolution, inner, innerCandidates)

        then:
        SharedInterceptorRegistrations.peek(resolution, outer).is(outerCandidates)
        resolution.getBeanInterceptors(inner).is(innerCandidates)

        when:
        SharedInterceptorRegistrations.pop(resolution, outer, outerCandidates)

        then:
        resolution.getBeanInterceptors(outer).is(outerCandidates)
        SharedInterceptorRegistrations.peek(resolution, inner).is(innerCandidates)

        cleanup:
        resolution.close()
        context.close()
    }
}
