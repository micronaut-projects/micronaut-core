package io.micronaut.aop.lifecycle

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.InterceptorRegistry
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.chain.InterceptorChainFactory
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class InterceptorChainFactorySpec extends Specification {
    void 'build returns independent introduction chains and preserves around advice and arguments'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def method = Mock(ExecutableMethod)
        def factory = new InterceptorChainFactory(registry)
        def target = new Object()
        def calls = []
        MethodInterceptor around = { context ->
            calls << 'around'
            context.proceed()
        }
        MethodInterceptor introduction = { context ->
            calls << 'introduction'
            context.parameterValues[0]
        }

        when:
        def first = factory.build(target, method, [], InterceptorKind.INTRODUCTION, 'first')
        def second = factory.build(target, method, [], InterceptorKind.INTRODUCTION, 'second')

        then:
        2 * registry.resolveInterceptors(method, [], InterceptorKind.INTRODUCTION) >> ([introduction] as Interceptor[])
        2 * registry.resolveInterceptors(method, [], InterceptorKind.AROUND) >> ([around] as Interceptor[])
        first instanceof MethodInterceptorChain
        !first.is(second)
        first.kind == InterceptorKind.INTRODUCTION
        calls.empty

        when:
        def firstResult = first.proceed()
        def secondResult = second.proceed()

        then:
        firstResult == 'first'
        secondResult == 'second'
        calls == ['around', 'introduction', 'around', 'introduction']
        0 * method.invoke(_, _)
    }

    void 'build returns an invokable chain when no advice matches including nullable results'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def method = Mock(ExecutableMethod)
        def target = new Object()
        def factory = new InterceptorChainFactory(registry)

        when:
        def chain = factory.build(target, method, [], InterceptorKind.AROUND, 'argument')

        then:
        1 * registry.resolveInterceptors(method, [], InterceptorKind.AROUND) >> ([] as Interceptor[])
        chain instanceof MethodInterceptorChain
        0 * method.invoke(_, _)

        when:
        def result = chain.proceed()

        then:
        1 * method.invoke(target, ['argument'] as Object[]) >> null
        result == null
    }
}
