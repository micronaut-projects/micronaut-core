package io.micronaut.aop.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ProxyBeanDefinition

/**
 * The proxy of a bean restricted to the types it exposes is found from its target definition, although the
 * target is no candidate of its own class. Only the definitions are looked up: creating such a bean fails
 * today, as the proxy looks its target up by the class the target does not expose.
 */
class ProxyOfTypedTargetSpec extends AbstractTypeElementSpec {

    void 'the proxy of a target exposed only as its interface is found from the target definition'() {
        given:
        def context = buildContext('typedproxy.DefaultGreeter', """
package typedproxy;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true)
@Retention(RUNTIME)
@interface Shouting {
}

@Singleton
@InterceptorBean(Shouting.class)
class ShoutingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed().toString().toUpperCase();
    }
}

interface Greeter {
    String greet(String name);
}

@Singleton
@Bean(typed = Greeter.class)
@Shouting
class DefaultGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "hello " + name;
    }
}
""")
        def greeterClass = context.classLoader.loadClass('typedproxy.Greeter')
        def proxy = context.getBeanDefinition(greeterClass)
        def target = context.findProxyTargetBeanDefinition(proxy).get()

        expect:
        proxy instanceof ProxyBeanDefinition
        target.exposedTypes == [greeterClass] as Set
        context.findProxyBeanDefinition(target).get().is(proxy)

        cleanup:
        context?.close()
    }
}
