package io.micronaut.aop.proxytarget

import io.micronaut.context.AbstractInitializableBeanDefinition
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanDefinitionRegistry
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ProxyBeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class ProxyTargetDefinitionLookupSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'ProxyTargetDefinitionLookupSpec'])

    void "the context finds the very target definition for a proxy definition"() {
        when: "the definition of a bean whose methods are proxied is obtained"
        BeanDefinition<ProxyingClass> proxy = context.getBeanDefinition(ProxyingClass)

        then: "it is the proxy standing in front of the class that was written"
        proxy instanceof ProxyBeanDefinition

        when: "the target is asked for by its definition and by type"
        ProxyBeanDefinition<ProxyingClass> proxyDefinition = (ProxyBeanDefinition<ProxyingClass>) proxy
        Optional<BeanDefinition<ProxyingClass>> byDefinition = context.findProxyTargetBeanDefinition(proxy)
        BeanDefinition<ProxyingClass> byType = context.getProxyTargetBeanDefinition(ProxyingClass, null)

        then: "both answer with the same instance"
        byDefinition.isPresent()
        byDefinition.get().is(byType)
        !byDefinition.get().isProxy()
        byDefinition.get().getClass() == proxyDefinition.getTargetDefinitionType()

        and: "the registry lookup the default delegates to answers the same"
        context.findBeanDefinitionByDefinitionClass(proxyDefinition.getTargetDefinitionType()).get().is(byType)

        and: "the proxy definition is itself reachable through its own class"
        context.findBeanDefinitionByDefinitionClass(proxy.getClass()).get().is(proxy)
    }

    void "a proxy definition identifies its target when its target type is ambiguous"() {
        given: "two proxied factory products with the same target type"
        List<ProxyBeanDefinition<AmbiguousProxyTarget>> proxies = context.allBeanDefinitions
            .findAll { it instanceof ProxyBeanDefinition && it.targetType == AmbiguousProxyTarget }
            .collect { (ProxyBeanDefinition<AmbiguousProxyTarget>) it }

        expect: "type resolution alone cannot select either target"
        proxies.size() == 2
        context.findProxyTargetBeanDefinition(AmbiguousProxyTarget, null).isEmpty()

        and: "each proxy resolves its own compiled target definition"
        proxies.every { proxy ->
            Optional<BeanDefinition<AmbiguousProxyTarget>> target = context.findProxyTargetBeanDefinition(proxy)
            target.isPresent() && target.get().getClass() == proxy.targetDefinitionType
        }
    }

    void "a definition that is not a proxy is found by its own class"() {
        given:
        BeanDefinition<ArgMutatingInterceptor> definition = context.getBeanDefinition(ArgMutatingInterceptor)

        expect:
        !(definition instanceof ProxyBeanDefinition)
        context.findBeanDefinitionByDefinitionClass(definition.getClass()).get().is(definition)
    }

    void "a class that is not a compiled definition is not found"() {
        expect:
        context.findBeanDefinitionByDefinitionClass(AbstractInitializableBeanDefinition).isEmpty()
    }

    void "the definition class lookup rejects null"() {
        when:
        context.findBeanDefinitionByDefinitionClass(null)

        then:
        thrown(NullPointerException)
    }

    void "the interface default finds a definition on a registry that does not override it"() {
        given: "a registry that only knows its references and inherits everything else"
        BeanDefinition<ArgMutatingInterceptor> resolved = context.getBeanDefinition(ArgMutatingInterceptor)
        BeanDefinitionRegistry registry = registryOver(BeanDefinitionRegistry, context.getBeanDefinitionReferences())

        when:
        Optional<BeanDefinition<ArgMutatingInterceptor>> found = registry.findBeanDefinitionByDefinitionClass(resolved.getClass())

        then: "the default loads the one matching reference"
        found.isPresent()
        found.get().getClass() == resolved.getClass()
        found.get().beanType == ArgMutatingInterceptor

        and: "a class no reference was compiled as is not found"
        registry.findBeanDefinitionByDefinitionClass(AbstractInitializableBeanDefinition).isEmpty()

        when:
        registry.findBeanDefinitionByDefinitionClass(null)

        then:
        thrown(NullPointerException)
    }

    void "the interface default answers only an enabled definition when the registry is a bean context"() {
        given: "a bean context that does not override the lookup"
        BeanDefinition<ArgMutatingInterceptor> resolved = context.getBeanDefinition(ArgMutatingInterceptor)
        BeanContext registry = registryOver(BeanContext, context.getBeanDefinitionReferences())

        when:
        Optional<BeanDefinition<ArgMutatingInterceptor>> found = registry.findBeanDefinitionByDefinitionClass(resolved.getClass())

        then:
        found.isPresent()
        found.get().getClass() == resolved.getClass()
    }

    /**
     * A registry of the given interface whose only knowledge is a fixed reference list; every default method of the
     * interface runs as written, every other method is refused, so the test exercises the default and nothing else.
     */
    private static <R> R registryOver(Class<R> type, Collection<BeanDefinitionReference<Object>> references) {
        InvocationHandler handler = { Object proxy, java.lang.reflect.Method method, Object[] args ->
            if (method.name == 'getBeanDefinitionReferences') {
                return references
            }
            if (method.default) {
                return InvocationHandler.invokeDefault(proxy, method, args)
            }
            throw new UnsupportedOperationException("not part of this test: " + method)
        }
        return type.cast(Proxy.newProxyInstance(type.classLoader, [type] as Class[], handler))
    }
}
