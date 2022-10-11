package io.micronaut.aop.introduction.with_around

import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.context.BeanContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.inject.ExecutableMethod

import javax.inject.Singleton

@Singleton
class ProxyAdviceInterceptor implements MethodInterceptor<Object, Object> {

    private final BeanContext beanContext

    ProxyAdviceInterceptor(BeanContext beanContext) {
        this.beanContext = beanContext
    }

    @Nullable
    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.getMethodName().equalsIgnoreCase("getId")) {
            // Test invocation delegation
            if (context.getTarget() instanceof MyBean5) {
                MyBean5 delegate = new MyBean5()
                delegate.setId(1L)
                return context.getExecutableMethod().invoke(delegate, context.getParameterValues());
            } else if (context.getTarget() instanceof MyBean6) {
                try {
                    ExecutableMethod<MyBean6, Object> proxyTargetMethod = beanContext.getProxyTargetMethod(MyBean6.class, context.getMethodName(), context.getArgumentTypes())
                    MyBean6 delegate = new MyBean6()
                    delegate.setId(1L)
                    return proxyTargetMethod.invoke(delegate, context.getParameterValues());
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException(e)
                }
            } else {
                return 1L
            }
        }
        if (context.getMethodName().equalsIgnoreCase("isProxy")) {
            return true
        }
        return context.proceed()
    }
}
