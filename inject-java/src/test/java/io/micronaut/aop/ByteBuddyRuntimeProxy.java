package io.micronaut.aop;

import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.Qualifier;
import io.micronaut.core.type.TypeInformation;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Morph;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

@Singleton
@NullMarked
public class ByteBuddyRuntimeProxy implements RuntimeProxyCreator {

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        Class<T> targetType = proxyDefinition.proxyBeanDefinition().getBeanType();

        ByteBuddy byteBuddy = new ByteBuddy();
        DynamicType.Builder<?> builder;
        if (targetType.isInterface()) {
            builder = byteBuddy.subclass(Object.class).implement(targetType);
        } else {
            builder = byteBuddy.subclass(targetType);
        }
        builder = builder.name(targetType.getName() + "$ByteBuddyProxy");

        T proxyTarget = null;
        if (proxyDefinition.proxyTarget()) {
            proxyTarget = proxyDefinition.targetBean();
            builder = builder.implement(InterceptedProxy.class);
            try {
                T finalProxyTarget = proxyTarget;
                builder = builder.method(
                    ElementMatchers.is(InterceptedProxy.class.getMethod("interceptedTarget"))
                ).intercept(InvocationHandlerAdapter.of((proxy, method, args) -> finalProxyTarget));
                builder = builder.method(
                    ElementMatchers.is(InterceptedProxy.class.getMethod("hasCachedInterceptedTarget"))
                ).intercept(InvocationHandlerAdapter.of((proxy, method, args) -> true));
                builder = builder.method(
                    ElementMatchers.is(InterceptedProxy.class.getMethod("$withBeanQualifier", Qualifier.class))
                ).intercept(InvocationHandlerAdapter.of((proxy, method, args) -> null));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            ExecutableMethod<T, ?> executableMethod = interceptedMethod.executableMethod();
            Method targetMethod = executableMethod.getTargetMethod();
            if (executableMethod.isAbstract()) {
                builder = builder.method(ElementMatchers.is(targetMethod))
                    .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            return new MethodInterceptorChain(interceptedMethod.interceptors(), proxy, executableMethod, args).proceed();
                        }
                    }));
            } else if (proxyDefinition.proxyTarget()) {
                builder = builder.method(ElementMatchers.is(targetMethod))
                    .intercept(MethodDelegation
                        .withDefaultConfiguration()
                        .to(new ProxyTargetInterceptor<>(interceptedMethod.executableMethod(), interceptedMethod.interceptors(), proxyTarget)));
            } else {
                builder = builder.method(ElementMatchers.is(targetMethod))
                    .intercept(MethodDelegation
                        .withDefaultConfiguration()
                        .withBinders(Morph.Binder.install(SuperCall.class))
                        .to(new TargetInterceptor<>(interceptedMethod.executableMethod(), interceptedMethod.interceptors())));
            }
        }
        try {
            MethodHandles.Lookup privateLookup;
            try {
                privateLookup = MethodHandles.privateLookupIn(targetType, MethodHandles.lookup());
            } catch (IllegalAccessException ex) {
                privateLookup = MethodHandles.lookup();
            }
            Class<? extends T> proxyType = builder.make()
                .load(proxyDefinition.beanContext().getClassLoader(),
                    net.bytebuddy.dynamic.loading.ClassLoadingStrategy.UsingLookup.of(privateLookup))
                .getLoaded().asSubclass(targetType);
            return proxyType.getDeclaredConstructor(
                Arrays.stream(proxyDefinition.constructorArguments()).map(TypeInformation::getType).toArray(Class<?>[]::new)
            ).newInstance(proxyDefinition.constructorValues());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ProxyTargetInterceptor<T> {

        private final ExecutableMethod<T, Object> executableMethod;
        private final Interceptor<T, Object>[] interceptors;
        private final T proxyTarget;

        public ProxyTargetInterceptor(ExecutableMethod<T, Object> executableMethod, Interceptor<T, Object>[] interceptors, T proxyTarget) {
            this.executableMethod = executableMethod;
            this.interceptors = interceptors;
            this.proxyTarget = proxyTarget;
        }

        @RuntimeType
        @Nullable
        public Object intercept(@AllArguments Object[] args
        ) throws Exception {
            return new MethodInterceptorChain<>(interceptors, proxyTarget, executableMethod, args).proceed();
        }
    }

    public static class TargetInterceptor<T> {

        private final ExecutableMethod<T, Object> executableMethod;
        private final Interceptor<T, Object>[] interceptors;

        public TargetInterceptor(ExecutableMethod<T, Object> executableMethod, Interceptor<T, Object>[] interceptors) {
            this.executableMethod = executableMethod;
            this.interceptors = interceptors;
        }

        @RuntimeType
        @Nullable
        public Object intercept(
            @This T target,
            @AllArguments Object[] args,
            @Morph SuperCall superCall
        ) throws Exception {

            io.micronaut.aop.Interceptor<T, Object>[] newInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, io.micronaut.aop.Interceptor[].class);
            newInterceptors[newInterceptors.length - 1] = context -> {
                try {
                    return superCall.call(context.getParameterValues());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };

            return new MethodInterceptorChain<>(newInterceptors, target, executableMethod, args).proceed();
        }
    }

    public interface SuperCall {
        Object call(Object[] args) throws Exception;
    }
}
