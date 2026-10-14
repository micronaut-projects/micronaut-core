/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.context.retain.proxy

import io.micronaut.aop.Intercepted
import io.micronaut.aop.Interceptor
import io.micronaut.aop.InterceptedProxy
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.BeanRegistration
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.scope.CustomScope
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.context.retain.proxy.ProxyBeans.*
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Array
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Retention across a restart around every kind of proxy: a singleton that holds a proxy, a proxy that holds a retained
 * singleton, the proxies themselves and the interceptors of every scope. A proxy resolves its target, its interceptors
 * or its scope through the context that made it, so it is never retained, nor is what holds it; what a proxy holds is
 * retained, and the next context wraps it in proxies of its own.
 */
class RetainedProxySpec extends Specification {

    def setup() {
        ProxyBeans.DESTROYED.clear()
    }

    @Unroll
    void "a singleton holding #description is refused, and made again by the next context"() {
        given:
        ApplicationContext first = start(List.of())
        Object held = first.getBean(holder)

        when: "the predicate accepts it"
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.beanType == holder }

        then: "it is destroyed with the stopped context, not retained"
        retained.isEmpty()
        ProxyBeans.DESTROYED.contains(holder) || !(held instanceof Holder)

        when:
        ApplicationContext second = start(retained)

        then: "the next context makes its own, on proxies of its own"
        !second.getBean(holder).is(held)

        cleanup:
        second?.close()

        where:
        holder                     | description
        AroundHolder               | 'an @Around proxy with a singleton interceptor'
        TargetHolder               | 'an @Around(proxyTarget = true) proxy'
        PrototypeInterceptedHolder | 'an @Around proxy with a prototype interceptor'
        ContextInterceptedHolder   | 'an @Around proxy whose interceptor holds the context'
        IntroductionHolder         | 'an @Introduction proxy'
        RefreshableHolder          | 'a @Refreshable scoped proxy'
        RequestHolder              | 'a @RequestScope scoped proxy'
        ThreadHolder               | 'a @ThreadLocal scoped proxy'
        TenantHolder               | 'a proxy of a custom @ScopedProxy scope'
        CachedLazyHolder           | 'a lazy proxy that caches its target'
        UncachedLazyHolder         | 'a lazy proxy that resolves its target on every call'
        Outer                      | 'a singleton that holds a proxy'
    }

    @Unroll
    void "#description is refused itself"() {
        given:
        ApplicationContext first = start(List.of())
        Object proxy = first.getBean(type)
        proxy.call()

        when: "the predicate accepts every registration of its type, the proxy and, for a proxy target, its target"
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { type.isInstance(it.bean) }

        then: "no proxy is retained"
        !retained*.bean.any { it.is(proxy) }
        !retained*.bean.any { it instanceof Intercepted }

        when:
        ApplicationContext second = start(retained)

        then:
        !second.getBean(type).is(proxy)
        second.getBean(type).call() == result

        cleanup:
        second?.close()

        where:
        type                        | result       | description
        AroundService               | 'around'     | 'an @Around proxy'
        PrototypeInterceptedService | 'prototype'  | 'an @Around proxy with a prototype interceptor'
        ContextInterceptedService   | 'contextual' | 'an @Around proxy whose interceptor holds the context'
    }

    void "an @Introduction proxy is refused itself"() {
        given:
        ApplicationContext first = start(List.of())
        Greeter greeter = first.getBean(Greeter)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.bean instanceof Greeter }
        ApplicationContext second = start(retained)

        then:
        retained.isEmpty()
        !second.getBean(Greeter).is(greeter)
        second.getBean(Greeter).greet() == 'stub'

        cleanup:
        second?.close()
    }

    void "the target of an @Around(proxyTarget = true) proxy is retained without its proxy, which the next context makes again around it"() {
        given:
        ApplicationContext first = start(List.of())
        TargetService proxy = first.getBean(TargetService)
        TargetService target = ((InterceptedProxy<TargetService>) proxy).interceptedTarget()
        CountingInterceptor interceptor = first.getBean(CountingInterceptor)

        when: "the predicate accepts the proxy and its target"
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.bean instanceof TargetService }

        then: "the target alone is retained, and destroying its proxy did not destroy it"
        retained*.bean == [target]
        !ProxyBeans.DESTROYED.contains(TargetService)
        noReferenceToStoppedState(retained, first, [])

        when:
        ApplicationContext second = start(retained)
        TargetService newProxy = second.getBean(TargetService)

        then: "a new proxy, with the next context's interceptor, wraps the same target"
        !newProxy.is(proxy)
        ((InterceptedProxy<TargetService>) newProxy).interceptedTarget().is(target)
        !second.getBean(CountingInterceptor).is(interceptor)
        newProxy.call() == 'target'

        when:
        second.close()

        then: "the target is destroyed with the context that adopted it"
        ProxyBeans.DESTROYED.contains(TargetService)
    }

    @Unroll
    void "a singleton interceptor holding nothing of the context is retained, and the next context's proxies use it: #type.simpleName"() {
        given:
        ApplicationContext first = start(List.of())
        first.getBean(AroundService).call()
        first.getBean(Greeter).greet()
        Object interceptor = first.getBean(type)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.beanType == type }

        then:
        retained*.bean == [interceptor]
        noReferenceToStoppedState(retained, first, [interceptor])

        when:
        ApplicationContext second = start(retained)

        then: "the adopted instance is the interceptor of the next context's proxies"
        second.getBean(type).is(interceptor)
        second.getBean(AroundService).call() == 'around'
        second.getBean(Greeter).greet() == 'stub'
        interceptorsOf(second.getBean(proxyType)).any { it.is(interceptor) }

        cleanup:
        second?.close()

        where:
        type                | proxyType
        CountingInterceptor | AroundService
        StubInterceptor     | Greeter
    }

    void "an interceptor that holds the context is refused, and a prototype interceptor is never retained on its own"() {
        given:
        ApplicationContext first = start(List.of())
        first.getBean(ContextInterceptedService).call()
        first.getBean(PrototypeInterceptedService).call()
        ContextualInterceptor contextual = first.getBean(ContextualInterceptor)

        when: "the predicate accepts every interceptor"
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.bean instanceof Interceptor }

        then:
        !retained*.bean.any { it.is(contextual) }
        !retained*.bean.any { it instanceof PrototypeInterceptor }
        noReferenceToStoppedState(retained, first, retained*.bean)

        when:
        ApplicationContext second = start(retained)

        then:
        !second.getBean(ContextualInterceptor).is(contextual)
        second.getBean(ContextualInterceptor).context.is(second)
        second.getBean(ContextInterceptedService).call() == 'contextual'

        cleanup:
        second?.close()
    }

    void "a custom scope is refused: it holds the instances created in the stopped context"() {
        given:
        ApplicationContext first = start(List.of())
        TenantScope scope = first.getBean(TenantScope)
        TenantUser user = first.getBean(TenantUser)
        user.plain()
        TenantUser target = ((InterceptedProxy<TenantUser>) user).interceptedTarget()

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.bean instanceof CustomScope }

        then:
        !retained*.bean.any { it.is(scope) }
        retained*.bean.every { !(it instanceof CustomScope) }

        when:
        ApplicationContext second = start(retained)

        then: "the next context has a scope of its own, with an instance of its own"
        !second.getBean(TenantScope).is(scope)
        !((InterceptedProxy<TenantUser>) second.getBean(TenantUser)).interceptedTarget().is(target)

        cleanup:
        second?.close()
    }

    void "a singleton that every kind of proxy holds is retained, and the next context's proxies hold it"() {
        given:
        ApplicationContext first = start(List.of())
        ProxyUsers users = first.getBean(ProxyUsers)
        Plain plain = users.around.plain()
        assert users.target.plain().is(plain)
        assert users.refreshable.plain().is(plain)
        assert users.tenant.plain().is(plain)
        assert users.cachedLazy.plain().is(plain)
        assert users.greeter.plain().is(plain)

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.beanType == Plain }

        then: "it is retained alone, undestroyed, and holds nothing of the stopped context"
        retained*.bean == [plain]
        !ProxyBeans.DESTROYED.contains(Plain)
        noReferenceToStoppedState(retained, first, [])

        when:
        ApplicationContext second = start(retained)
        ProxyUsers newUsers = second.getBean(ProxyUsers)

        then: "every new proxy reaches the same instance"
        !newUsers.is(users)
        newUsers.around.plain().is(plain)
        newUsers.target.plain().is(plain)
        newUsers.refreshable.plain().is(plain)
        newUsers.tenant.plain().is(plain)
        newUsers.cachedLazy.plain().is(plain)
        newUsers.greeter.plain().is(plain)

        when:
        second.close()

        then:
        ProxyBeans.DESTROYED.contains(Plain)
    }

    void "accepting every singleton keeps no proxy, no scope and no interceptor of the stopped context"() {
        given: "a context where every kind of proxy has been made and called"
        ApplicationContext first = start(List.of())
        [AroundHolder, TargetHolder, PrototypeInterceptedHolder, ContextInterceptedHolder, IntroductionHolder,
         RefreshableHolder, RequestHolder, ThreadHolder, TenantHolder, CachedLazyHolder, UncachedLazyHolder, Outer].each {
            first.getBean(it)
        }
        ProxyUsers users = first.getBean(ProxyUsers)
        users.refreshable.plain()
        users.tenant.plain()
        users.cachedLazy.plain()
        first.getBean(ThreadService).call()
        first.getBean(TenantService).call()

        when:
        Collection<BeanRegistration<?>> retained = stopRetaining(first) { it.beanType.name.contains('.retain.proxy.') }
        List<Object> beans = retained*.bean

        then: "what is retained is what holds nothing bound to the stopped context"
        beans*.getClass().toSet() == [Plain, TargetService, TargetUser, CountingInterceptor, StubInterceptor] as Set
        noReferenceToStoppedState(retained, first, beans)

        when:
        ApplicationContext second = start(retained)

        then: "the next context works on top of it"
        second.getBean(ProxyUsers).around.plain().is(users.around.plain())
        second.getBean(ProxyUsers).target.plain().is(users.around.plain())
        second.getBean(Outer).inner.proxy.call() == 'around'

        cleanup:
        second?.close()
    }

    private static ApplicationContext start(Collection<BeanRegistration<?>> retained) {
        return ApplicationContext.builder()
            .properties('spec.name': 'RetainedProxySpec')
            .beanDependencyTrackingEnabled(true)
            .retainedRegistrations(retained)
            .start()
    }

    private static Collection<BeanRegistration<?>> stopRetaining(ApplicationContext context, Closure<Boolean> retain) {
        return ((DefaultBeanContext) context).stopRetaining { BeanRegistration<?> registration -> retain.call(registration) as boolean }
    }

    private static List<Object> interceptorsOf(Object proxy) {
        List<Object> found = []
        reachable(proxy) { Object value, String path ->
            if (value instanceof Interceptor) {
                found << value
            }
        }
        return found
    }

    /**
     * Walks everything the retained beans reach through their fields and asserts none of it is the stopped context, a
     * scope, a resolution context, a proxy, or an interceptor that is not itself retained.
     */
    private static boolean noReferenceToStoppedState(Collection<BeanRegistration<?>> retained, ApplicationContext stopped,
                                                     List<Object> retainedBeans) {
        List<String> leaks = []
        Set<Object> allowed = Collections.newSetFromMap(new IdentityHashMap<>())
        allowed.addAll(retainedBeans)
        for (BeanRegistration<?> registration : retained) {
            reachable(registration.bean) { Object value, String path ->
                if (value.is(stopped)
                    || value instanceof BeanResolutionContext
                    || value instanceof CustomScope && !allowed.contains(value)
                    || value instanceof Intercepted
                    || value instanceof Interceptor && !allowed.contains(value)) {
                    leaks << "${registration.bean.getClass().simpleName}${path} -> ${value.getClass().name}"
                }
            }
        }
        assert leaks.isEmpty()
        return true
    }

    private static void reachable(Object root, Closure<?> visitor) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>())
        Deque<List<Object>> queue = new ArrayDeque<>()
        queue.add([root, ''])
        while (!queue.isEmpty()) {
            List<Object> next = queue.poll()
            Object value = next[0]
            String path = next[1]
            if (value == null || value instanceof Class || value instanceof CharSequence || value instanceof Number
                || value instanceof Boolean || value instanceof Character || value instanceof Enum
                || value instanceof Argument || !visited.add(value)) {
                continue
            }
            if (!value.is(root)) {
                visitor.call(value, path)
            }
            if (value.getClass().isArray()) {
                if (!value.getClass().componentType.isPrimitive()) {
                    int length = Array.getLength(value)
                    for (int i = 0; i < length; i++) {
                        queue.add([Array.get(value, i), "${path}[${i}]".toString()])
                    }
                }
                continue
            }
            if (value instanceof Map) {
                ((Map) value).each { k, v ->
                    queue.add([k, "${path}{key}".toString()])
                    queue.add([v, "${path}{${k}}".toString()])
                }
                continue
            }
            if (value instanceof Iterable && value.getClass().name.startsWith('java.')) {
                int i = 0
                for (Object element : (Iterable) value) {
                    queue.add([element, "${path}[${i++}]".toString()])
                }
                continue
            }
            String name = value.getClass().name
            if (name.startsWith('java.') || name.startsWith('jdk.') || name.startsWith('sun.')
                || value instanceof BeanContext || value instanceof BeanDefinition) {
                // the context is reported, not walked: a retained bean that reaches it already leaks it
                continue
            }
            for (Class<?> type = value.getClass(); type != null && type != Object; type = type.superclass) {
                for (Field field : type.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) || field.type.isPrimitive() || !field.trySetAccessible()) {
                        continue
                    }
                    queue.add([field.get(value), "${path}.${field.name}".toString()])
                }
            }
        }
    }
}
