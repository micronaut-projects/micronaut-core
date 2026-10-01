package io.micronaut.aop.lifecycle

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.InterceptorRegistry
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.chain.DefaultInterceptorChainFactory
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.aop.InvocationContext
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.exceptions.ConstructorAdviceException
import io.micronaut.core.beans.BeanConstructor
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

class InterceptorChainFactorySpec extends Specification {
    void 'build returns independent introduction chains and preserves around advice and arguments'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def method = Mock(ExecutableMethod)
        def factory = new DefaultInterceptorChainFactory(registry)
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
        def first = factory.buildMethodChain(target, method, [], InterceptorKind.INTRODUCTION, 'first')
        def second = factory.buildMethodChain(target, method, [], InterceptorKind.INTRODUCTION, 'second')

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
        def factory = new DefaultInterceptorChainFactory(registry)

        when:
        def chain = factory.buildMethodChain(target, method, [], InterceptorKind.AROUND, 'argument')

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

    void 'instance lifecycle execution rejects null advice results - #operation #entry'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def resolution = Mock(BeanResolutionContext)
        def definition = Mock(BeanDefinition) {
            getBeanType() >> Object
        }
        def method = Mock(ExecutableMethod)
        def factory = new DefaultInterceptorChainFactory(registry)
        def bean = new Object()
        MethodInterceptor advice = { context -> null }

        when:
        if (entry == 'factory') {
            factory."$operation"(resolution, definition, method, bean, [])
        } else {
            factory.buildLifecycleChain(resolution, definition, method, bean, kind, []).proceedLifecycle(definition)
        }

        then:
        1 * registry.resolveInterceptors(method, [], kind) >> ([advice] as Interceptor[])
        def failure = thrown(NullPointerException)
        failure.message.contains(kind.name())
        0 * method.invoke(_, _)

        where:
        [operation, entry] << [['initialize', 'dispose'], ['factory', 'chain']].combinations()
        kind = operation == 'initialize' ? InterceptorKind.POST_CONSTRUCT : InterceptorKind.PRE_DESTROY
    }

    void 'instance lifecycle execution treats explicit empty candidates as authoritative - #operation'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def resolution = Mock(BeanResolutionContext)
        def definition = Mock(BeanDefinition)
        def method = Mock(ExecutableMethod)
        def factory = new DefaultInterceptorChainFactory(registry)
        def bean = new Object()

        when:
        def result = factory."$operation"(resolution, definition, method, bean, [])

        then:
        1 * registry.resolveInterceptors(method, [], kind) >> ([] as Interceptor[])
        1 * method.invoke(bean, [] as Object[]) >> null
        result == null
        0 * resolution._

        where:
        operation << ['initialize', 'dispose']
        kind = operation == 'initialize' ? InterceptorKind.POST_CONSTRUCT : InterceptorKind.PRE_DESTROY
    }

    void 'instance construction distinguishes advice and body failures - #origin #entry'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def resolution = Mock(BeanResolutionContext)
        def definition = Mock(BeanDefinition)
        def constructor = Mock(BeanConstructor)
        def factory = new DefaultInterceptorChainFactory(registry)
        def failure = new IllegalStateException('construction failed')
        Interceptor advice = { InvocationContext context ->
            if (origin == 'advice') {
                throw failure
            }
            context.proceed()
        }

        when:
        if (entry == 'factory') {
            factory.instantiate(resolution, definition, constructor, [], 0)
        } else {
            factory.buildConstructorChain(resolution, definition, constructor, [], 0).instantiate()
        }

        then:
        1 * registry.resolveConstructorInterceptors(constructor, []) >> ([advice] as Interceptor[])
        (origin == 'body' ? 1 : 0) * constructor.instantiate([] as Object[]) >> { throw failure }
        def actual = thrown(RuntimeException)
        origin == 'advice' ? actual instanceof ConstructorAdviceException && actual.adviceCause.is(failure) : actual.is(failure)
        0 * resolution._

        where:
        [origin, entry] << [['advice', 'body'], ['factory', 'chain']].combinations()
    }

    void 'instance construction rejects null unadvised results - #entry'() {
        given:
        def registry = Mock(InterceptorRegistry)
        def resolution = Mock(BeanResolutionContext)
        def definition = Mock(BeanDefinition)
        def constructor = Mock(BeanConstructor)
        def factory = new DefaultInterceptorChainFactory(registry)

        when:
        if (entry == 'factory') {
            factory.instantiate(resolution, definition, constructor, [], 0)
        } else {
            factory.buildConstructorChain(resolution, definition, constructor, [], 0).instantiate()
        }

        then:
        1 * registry.resolveConstructorInterceptors(constructor, []) >> ([] as Interceptor[])
        1 * constructor.instantiate([] as Object[]) >> null
        def failure = thrown(NullPointerException)
        failure.message.contains('illegally returned null')
        0 * resolution._

        where:
        entry << ['factory', 'chain']
    }
}
