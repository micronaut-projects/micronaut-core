package io.micronaut.context.python.aop;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.aop.runtime.RuntimeProxyCreator;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.context.python.ContextHolder;
import io.micronaut.context.python.ValueCoercible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;

@Internal
@Singleton
@NullMarked
public final class PythonProxyCreator implements RuntimeProxyCreator {

    @Override
    public <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition) {
        T targetBean;
        Value value;
        if (proxyDefinition.introduction()) {
            Class<T> beanType = proxyDefinition.proxyBeanDefinition().getBeanType();
            value = ContextHolder.findClass(beanType.getPackageName(), beanType.getSimpleName());
            targetBean = null;
        } else {
            targetBean = proxyDefinition.targetBean();
            value = ((ValueCoercible) targetBean).$unbox();
        }
        boolean isIntroduction = proxyDefinition.introduction();
        for (RuntimeProxyDefinition.InterceptedMethod<T> interceptedMethod : proxyDefinition.interceptedMethods()) {
            ExecutableMethod<T, ?> executableMethod = interceptedMethod.executableMethod();
            Interceptor<T, ?>[] interceptors = interceptedMethod.interceptors();

            String methodName = executableMethod.getMethodName();
            Value originalFunction = value.getMember(methodName);
            ProxyExecutable proxiedFunction = args -> {
                Object[] javaArgs = fromPolyglotArray(args);
                Interceptor<T, ?>[] finalInterceptors;
                if (isIntroduction) {
                    finalInterceptors = interceptors;
                } else {
                    if (originalFunction == null) {
                        throw new IllegalStateException("No original function found for method: " + executableMethod);
                    }
                    finalInterceptors = Arrays.copyOf(interceptors, interceptors.length + 1, Interceptor[].class);
                    finalInterceptors[finalInterceptors.length - 1] = invocationContext -> originalFunction.execute(
                        toPolyglotArray(invocationContext.getParameterValues(), originalFunction.getContext())
                    );
                }
                return new MethodInterceptorChain(finalInterceptors, targetBean, executableMethod, javaArgs).proceed();
            };
            value.putMember(methodName, proxiedFunction);
        }
        if (isIntroduction) {
            fillAllAbstractMethods(value);
            try {
                return proxyDefinition.proxyBeanDefinition().getBeanType().getDeclaredConstructor(Value.class).newInstance(value.newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return targetBean;
    }

    private void fillAllAbstractMethods(Value pythonClass) {
        Context context = pythonClass.getContext();
        if (pythonClass.hasMember("__abstractmethods__")) {
            Value abstractMethodsValue = pythonClass.getMember("__abstractmethods__");
            if (abstractMethodsValue != null && abstractMethodsValue.hasIterator()) {
                Value iterator = abstractMethodsValue.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    Value next = iterator.getIteratorNextElement();
                    if (next != null && next.isString()) {
                        String methodName = next.asString();
                        Value existingMember = pythonClass.getMember(methodName);
                        if (existingMember == null || !existingMember.canExecute()) {
                            pythonClass.putMember(methodName, (ProxyExecutable) args -> null);
                        }
                    }
                }
            }
            Value emptyAbstractMethods = context.eval("python", "frozenset()");
            pythonClass.putMember("__abstractmethods__", emptyAbstractMethods);
        }
    }

    private Object[] toPolyglotArray(Object[] in, Context context) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Object parameterValue = in[i];
            out[i] = context.asValue(parameterValue);
        }
        return out;
    }

    private Object[] fromPolyglotArray(Value[] in) {
        Object[] out = new Object[in.length];
        for (int i = 0; i < in.length; i++) {
            Value arg = in[i];
            out[i] = arg.as(Object.class);
        }
        return out;
    }
}
