package io.micronaut.aop.lazyproxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

/**
 * A {@code @Nullable} factory method is allowed to produce {@code null}. When the produced bean is
 * behind a lazy proxy, resolving the proxy target must route the null through
 * {@link BeanResolutionCustomizer#resolveNullBean} the way an ordinary bean lookup does, instead of
 * handing the proxy a null target that fails with a {@link NullPointerException} on the first
 * intercepted call.
 */
class ProxyTargetNullBeanSpec extends AbstractTypeElementSpec {

    void 'test a null proxy target is resolved through the customizer'() {
        given:
        def context = buildContext('''
package proxynulltarget;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true, lazy = true)
@Retention(RUNTIME)
@interface LazilyProxied {
}

@Singleton
@InterceptorBean(LazilyProxied.class)
class LazilyProxiedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}

class Product {
    public String name() {
        return "substitute";
    }
}

@Factory
class ProductFactory {

    @LazilyProxied
    @Prototype
    @Nullable
    Product product() {
        return null;
    }
}
''')
        Class<?> productClass = context.classLoader.loadClass('proxynulltarget.Product')

        when: 'the bean is looked up'
        def proxy = context.getBean(productClass)

        then: 'a lazy proxy is returned'
        proxy.class != productClass

        when: 'an intercepted call resolves the proxy target the factory produced as null'
        def result = proxy.name()

        then: 'the customizer substitute is used instead of failing with a NullPointerException'
        result == 'substitute'

        when: 'the proxy target is resolved directly'
        def target = context.getProxyTargetBean(productClass, null)

        then:
        target != null
        target.name() == 'substitute'

        cleanup:
        context.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            Optional<?> resolveNullBean(Argument<?> requestedBeanType, Argument<?> resolvedBeanType, BeanDefinition<?> beanDefinition) {
                if (beanDefinition.beanType.name == 'proxynulltarget.Product') {
                    def constructor = beanDefinition.beanType.getDeclaredConstructor()
                    constructor.accessible = true
                    return Optional.of(constructor.newInstance())
                }
                return Optional.empty()
            }
        })
    }
}
