package io.micronaut.aop.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class LazyHotswapInterceptorsPerTargetSpec extends AbstractTypeElementSpec {

    void "a lazy hotswap proxy caching its target with interceptors per target is generated and intercepts"() {
        given:
        def context = buildContext('''
package test;

import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Around(proxyTarget = true, lazy = true, hotswap = true, cacheableLazyTarget = true, lazyInterceptorsPerTarget = true)
@interface Traced {
}

@Singleton
@InterceptorBean(Traced.class)
class TracedInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return "traced " + context.proceed();
    }
}

@Singleton
@Traced
class Service {
    public String hello() {
        return "hello";
    }
}
''')
        def service = getBean(context, 'test.Service')

        expect:
        service.hello() == 'traced hello'
        service.hello() == 'traced hello'

        cleanup:
        context.close()
    }
}
