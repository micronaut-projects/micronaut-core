package io.micronaut.aop.simple

import io.micronaut.aop.Interceptor
import io.micronaut.aop.InvocationContext
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.aop.runtime.RuntimeProxyCreator
import io.micronaut.aop.runtime.RuntimeProxyDefinition
import jakarta.inject.Singleton

@Singleton
class RuntimeGroovyProxyCreator implements RuntimeProxyCreator {

    @Override
    def <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        T target = proxyDefinition.targetBean()

        def map = proxyDefinition.interceptedMethods().collectEntries { interceptedMethod ->
            def executableMethod = interceptedMethod.executableMethod()
            [target.metaClass.pickMethod(executableMethod.name, executableMethod.getArgumentTypes()), interceptedMethod]
        }

        target.metaClass.invokeMethod { name, args ->
            def theDelegate = delegate
            MetaMethod metaMethod = theDelegate.class.metaClass.getMetaMethod(name, args)
            RuntimeProxyDefinition.InterceptedMethod interceptedMethod = map[metaMethod]
            def interceptors = interceptedMethod.interceptors()
            def executableMethod = interceptedMethod.executableMethod()

            io.micronaut.aop.Interceptor<T, Object>[] newInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, io.micronaut.aop.Interceptor[].class);
            newInterceptors[newInterceptors.length - 1] = new Interceptor<T, Object>() {
                @Override
                Object intercept(InvocationContext<T, Object> context) {
                    try {
                        return metaMethod.invoke(theDelegate, args )
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            return new MethodInterceptorChain<>(newInterceptors, target, executableMethod, args).proceed();

        }


        return target
    }

}
