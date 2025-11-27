package io.micronaut.aop;

import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

@Singleton
@NullMarked
public class JdkRuntimeProxy implements RuntimeProxyCreator {

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        Class<T> targetType = proxyDefinition.proxyBeanDefinition().getBeanType();
        if (!targetType.isInterface()) {
            throw new IllegalArgumentException("JDK runtime proxy supports interface types only: " + targetType);
        }

        InvocationHandler handler = new RuntimeInvocationHandler<>(proxyDefinition, targetType);
        Object proxy = Proxy.newProxyInstance(proxyDefinition.beanContext().getClassLoader(), new Class[]{targetType}, handler);
        return targetType.cast(proxy);
    }

    private static final class RuntimeInvocationHandler<T> implements InvocationHandler {

        private final RuntimeProxyDefinition<T> proxyDefinition;
        private final Class<T> targetType;
        private final Map<Method, RuntimeProxyDefinition.InterceptedMethod<T>> interceptedMethods;

        RuntimeInvocationHandler(RuntimeProxyDefinition<T> proxyDefinition, Class<T> targetType) {
            this.proxyDefinition = proxyDefinition;
            this.targetType = targetType;
            this.interceptedMethods = new HashMap<>();
            for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
                Method targetMethod = interceptedMethod.executableMethod().getTargetMethod();
                targetMethod.setAccessible(true);
                interceptedMethods.put(targetMethod, interceptedMethod);
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }

            RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod = interceptedMethods.get(method);
            if (interceptedMethod != null) {
                Object[] parameters = args == null ? new Object[0] : args;
                ExecutableMethod<T, Object> executableMethod = interceptedMethod.executableMethod();
                return new MethodInterceptorChain<>(interceptedMethod.interceptors(), targetType.cast(proxy), executableMethod, parameters).proceed();
            }

            if (method.isDefault()) {
                return invokeDefaultMethod(proxy, method, args);
            }

            throw new UnsupportedOperationException("Method " + method + " is not intercepted by " + targetType.getName());
        }

        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "equals" -> proxy == (args != null && args.length == 1 ? args[0] : null);
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> targetType.getName() + "@" + Integer.toHexString(System.identityHashCode(proxy));
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup);
            MethodHandle methodHandle = privateLookup.in(method.getDeclaringClass()).unreflectSpecial(method, method.getDeclaringClass());
            return methodHandle.bindTo(proxy).invokeWithArguments(args == null ? new Object[0] : args);
        }
    }
}
