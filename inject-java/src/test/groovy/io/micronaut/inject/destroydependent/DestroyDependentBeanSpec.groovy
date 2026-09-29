package io.micronaut.inject.destroydependent

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.DefaultBeanResolutionContext
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class DestroyDependentBeanSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run("spec": getClass().getSimpleName())

    def setup() {
        SharedBean.created = 0
        SharedBean.destroyed = 0
        PrototypeBean.destroyed = 0
        NestedDependency.destroyed = 0
        LifeCycleBean.stopped = 0
    }

    void "a scoped proxy destroyed as a dependent leaves the bean of the scope alive"() {
        given:
        SharedScopeImpl scope = context.getBean(SharedScopeImpl)
        def resolved = resolveAsDependent(SharedBean)
        SharedBean proxy = resolved.bean
        int id = proxy.id()

        expect: "the proxy is what the resolution reported, and its target is in the scope"
        resolved.registration.bean.is(proxy)
        scope.beans.size() == 1
        SharedBean.created == 1

        when:
        context.destroyDependentBean(resolved.registration)

        then: "the target is neither destroyed nor taken out of the scope"
        SharedBean.destroyed == 0
        scope.beans.size() == 1
        context.getBean(SharedBean).id() == id
        SharedBean.created == 1
    }

    void "a scoped proxy destroyed in its own right takes the bean out of the scope"() {
        given:
        SharedScopeImpl scope = context.getBean(SharedScopeImpl)
        def resolved = resolveAsDependent(SharedBean)
        resolved.bean.id()

        when:
        context.destroyBean(resolved.registration)

        then:
        SharedBean.destroyed == 1
        scope.beans.isEmpty()
    }

    void "a prototype destroyed as a dependent gets its pre-destroy, and its own dependents are destroyed"() {
        given:
        def resolved = resolveAsDependent(PrototypeBean)

        when:
        context.destroyDependentBean(resolved.registration)

        then:
        PrototypeBean.destroyed == 1
        NestedDependency.destroyed == 1
    }

    void "a life cycle bean destroyed as a dependent is not stopped"() {
        given:
        def dependent = resolveAsDependent(LifeCycleBean)
        def own = resolveAsDependent(LifeCycleBean)

        when:
        context.destroyDependentBean(dependent.registration)

        then: "it is destroyed, with its own dependents, but not stopped"
        NestedDependency.destroyed == 1
        LifeCycleBean.stopped == 0

        when: "destroyed in its own right, it is"
        context.destroyBean(own.registration)

        then:
        NestedDependency.destroyed == 2
        LifeCycleBean.stopped == 1
    }

    void "destroying a dependent twice destroys it once"() {
        given:
        def prototype = resolveAsDependent(PrototypeBean)
        def closedFirst = resolveAsDependent(PrototypeBean)
        def scoped = resolveAsDependent(SharedBean)
        int id = scoped.bean.id()

        when:
        context.destroyDependentBean(prototype.registration)
        context.destroyDependentBean(prototype.registration)
        prototype.registration.close()

        then:
        PrototypeBean.destroyed == 1
        NestedDependency.destroyed == 1

        when:
        closedFirst.registration.close()
        context.destroyDependentBean(closedFirst.registration)

        then:
        PrototypeBean.destroyed == 2
        NestedDependency.destroyed == 2

        when:
        context.destroyDependentBean(scoped.registration)
        context.destroyDependentBean(scoped.registration)

        then:
        SharedBean.destroyed == 0
        context.getBean(SharedBean).id() == id
    }

    void "a context that does not implement it destroys the registration in its own right"() {
        given:
        BeanContext beanContext = (BeanContext) Proxy.newProxyInstance(
                getClass().classLoader,
                [BeanContext] as Class[],
                { Object proxy, Method method, Object[] args ->
                    method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : method.invoke(context, args)
                } as InvocationHandler
        )
        def resolved = resolveAsDependent(LifeCycleBean)

        when:
        beanContext.destroyDependentBean(resolved.registration)

        then:
        NestedDependency.destroyed == 1
        LifeCycleBean.stopped == 1
    }

    private <T> Resolved<T> resolveAsDependent(Class<T> type) {
        try (def resolutionContext = new DefaultBeanResolutionContext(context, null)) {
            T bean = resolutionContext.getBean(type)
            List<BeanRegistration<?>> dependents = resolutionContext.getAndResetDependentBeans()
            assert dependents.size() == 1
            return new Resolved<T>(bean, (BeanRegistration<T>) dependents[0])
        }
    }

    static class Resolved<T> {
        final T bean
        final BeanRegistration<T> registration

        Resolved(T bean, BeanRegistration<T> registration) {
            this.bean = bean
            this.registration = registration
        }
    }
}
