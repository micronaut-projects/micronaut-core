package io.micronaut.inject.scope.custom.definitionlookup

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanDestructionException
import io.micronaut.inject.ProxyBeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

class CustomScopeDefinitionLookupSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run("spec": getClass().getSimpleName(), "proxies.one": "one")

    def setup() {
        PlainBean.created = 0
        PlainBean.destroyed = 0
        FailingDestroyBean.created = 0
        ProxiedBean.created = 0
        ProxiedBean.destroyed = 0
        LockProbeBean.otherThreadAnswered = false
    }

    void "the registration held for a definition can be found and removed by that definition"() {
        given:
        LookupScopeImpl scope = context.getBean(LookupScopeImpl)
        def definition = context.getBeanDefinition(PlainBean)

        expect: "nothing is held before the bean is resolved"
        !scope.findBeanRegistration(definition).present

        when:
        PlainBean first = context.getBean(PlainBean)
        def registration = scope.findBeanRegistration(definition)

        then:
        registration.present
        registration.get().bean.is(first)
        registration.get().beanDefinition == definition
        scope.beans.size() == 1

        when:
        def removed = scope.remove(definition)

        then: "the held instance is destroyed and gone"
        removed.present
        removed.get().is(first)
        PlainBean.destroyed == 1
        scope.beans.isEmpty()
        !scope.findBeanRegistration(definition).present

        when:
        PlainBean second = context.getBean(PlainBean)

        then: "the next resolution creates a fresh instance"
        !second.is(first)
        PlainBean.created == 2
        PlainBean.destroyed == 1
    }

    void "a proxy definition finds and removes the target registration the scope holds"() {
        given:
        LookupProxyScopeImpl scope = context.getBean(LookupProxyScopeImpl)
        def proxyDefinition = context.getBeanDefinition(ProxiedBean)

        expect:
        proxyDefinition instanceof ProxyBeanDefinition

        when:
        ProxiedBean proxy = context.getBean(ProxiedBean)

        then: "the context hands out the proxy and the scope holds nothing until it is used"
        proxy instanceof Intercepted
        ProxiedBean.created == 0
        !scope.findBeanRegistration(proxyDefinition).present

        when:
        proxy.hello()
        def registration = scope.findBeanRegistration(proxyDefinition)

        then: "the lookup by the proxy definition lands on the target's registration"
        ProxiedBean.created == 1
        registration.present
        !(registration.get().bean instanceof Intercepted)
        registration.get().beanDefinition.getClass() == ((ProxyBeanDefinition) proxyDefinition).targetDefinitionType
        scope.beans.size() == 1

        when:
        def removed = scope.remove(proxyDefinition)

        then: "removing by the proxy definition destroys the target"
        removed.present
        removed.get().is(registration.get().bean)
        ProxiedBean.destroyed == 1
        scope.beans.isEmpty()

        when:
        proxy.hello()

        then: "the proxy creates a fresh target next"
        ProxiedBean.created == 2
        scope.beans.size() == 1
    }

    void "destroying a proxied scoped bean through the context destroys the target the scope holds"() {
        given:
        LookupProxyScopeImpl scope = context.getBean(LookupProxyScopeImpl)
        def registration = context.getBeanRegistration(ProxiedBean, null)
        registration.bean.hello()

        expect:
        ProxiedBean.created == 1
        scope.beans.size() == 1

        when:
        context.destroyBean(registration)

        then:
        ProxiedBean.destroyed == 1
        scope.beans.isEmpty()
    }

    void "a destruction failure on remove by definition propagates and the bean is still evicted"() {
        given:
        LookupScopeImpl scope = context.getBean(LookupScopeImpl)
        def definition = context.getBeanDefinition(FailingDestroyBean)
        FailingDestroyBean first = context.getBean(FailingDestroyBean)

        when:
        scope.remove(definition)

        then:
        def e = thrown(BeanDestructionException)
        e.cause instanceof IllegalStateException
        e.cause.message == "destroy failed on purpose"
        scope.beans.isEmpty()

        when:
        FailingDestroyBean second = context.getBean(FailingDestroyBean)

        then:
        !second.is(first)
        FailingDestroyBean.created == 2
    }

    void "remove by definition closes the bean outside the scope lock"() {
        given:
        LookupScopeImpl scope = context.getBean(LookupScopeImpl)
        def definition = context.getBeanDefinition(LockProbeBean)
        context.getBean(LockProbeBean)

        when:
        def removed = scope.remove(definition)

        then:
        removed.present
        LockProbeBean.otherThreadAnswered
        scope.beans.isEmpty()
    }

    void "remove by an unknown definition is empty and leaves the scope untouched"() {
        given:
        LookupScopeImpl scope = context.getBean(LookupScopeImpl)
        PlainBean bean = context.getBean(PlainBean)

        when:
        def removed = scope.remove(context.getBeanDefinition(FailingDestroyBean))

        then:
        !removed.present
        scope.beans.size() == 1
        context.getBean(PlainBean).is(bean)
        PlainBean.destroyed == 0
    }

    void "a delegated each-bean proxy definition finds and removes its target registration"() {
        given:
        LookupProxyScopeImpl scope = context.getBean(LookupProxyScopeImpl)
        def qualifier = Qualifiers.byName("one")
        def proxyDefinition = context.getBeanDefinition(EachProxiedBean, qualifier)
        def proxy = context.getBean(EachProxiedBean, qualifier)
        proxy.hello()

        expect:
        proxyDefinition.isProxy()
        !(proxyDefinition instanceof ProxyBeanDefinition)
        scope.beans.size() == 1
        scope.findBeanRegistration(proxyDefinition).present

        when:
        def removed = scope.remove(proxyDefinition)

        then:
        removed.present
        scope.beans.isEmpty()
    }
}
