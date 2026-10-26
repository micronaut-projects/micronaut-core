package io.micronaut.context.python.aop;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticAdviceTest {

    @Test
    @SuppressWarnings("unchecked")
    void anOverloadIsPickedByItsParameterTypesAndAnAmbiguousMismatchIsRefused() {
        ExecutableMethod<Object, Object> byInt = overload("price", int.class);
        ExecutableMethod<Object, Object> byName = overload("price", String.class);
        Object bean = new Object();

        // no interceptor: the executable method of the bean definition runs on the target
        StaticAdvice<Object> unadvised = new StaticAdvice<>(advised(List.of(), List.of(byInt, byName)), () -> bean);
        assertEquals("price(java.lang.String)", unadvised.proceed("price", new String[]{"java.lang.String"}, new Object[]{"x"}));
        assertEquals("price(int)", unadvised.proceed("price", new String[]{"int"}, new Object[]{1}));
        IllegalStateException executable = assertThrows(IllegalStateException.class, () ->
            unadvised.proceed("price", new String[]{"double"}, new Object[]{1.5}));
        assertTrue(executable.getMessage().contains("[price]") && executable.getMessage().contains("[double]"), executable.getMessage());

        // the intercepted methods of the proxy definition
        List<RuntimeProxyDefinition.InterceptedMethod<Object>> intercepted = List.of(
            new RuntimeProxyDefinition.InterceptedMethod<>(byInt, new Interceptor[0]),
            new RuntimeProxyDefinition.InterceptedMethod<>(byName, new Interceptor[0]));
        StaticAdvice<Object> advised = new StaticAdvice<>(advised(intercepted, List.of(byInt, byName)), () -> bean);
        IllegalStateException interceptedMismatch = assertThrows(IllegalStateException.class, () ->
            advised.proceed("price", new String[]{"double"}, new Object[]{1.5}));
        assertTrue(interceptedMismatch.getMessage().contains("[price]") && interceptedMismatch.getMessage().contains("[double]"), interceptedMismatch.getMessage());

        // a single overload of the arity is the one the body belongs to, however its types are spelled
        StaticAdvice<Object> single = new StaticAdvice<>(advised(List.of(), List.of(byInt)), () -> bean);
        assertEquals("price(int)", single.proceed("price", new String[]{"java.lang.Integer"}, new Object[]{1}));
    }

    @SuppressWarnings("unchecked")
    private static ExecutableMethod<Object, Object> overload(String name, Class<?>... parameterTypes) {
        Argument<?>[] arguments = Arrays.stream(parameterTypes).map(type -> Argument.of(type, "arg")).toArray(Argument<?>[]::new);
        String signature = name + "(" + String.join(",", Arrays.stream(parameterTypes).map(Class::getTypeName).toList()) + ")";
        return (ExecutableMethod<Object, Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{ExecutableMethod.class}, (self, method, args) -> switch (method.getName()) {
            case "getMethodName" -> name;
            case "getArguments" -> arguments;
            case "invoke" -> signature;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> signature;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static RuntimeProxyDefinition<Object> advised(List<RuntimeProxyDefinition.InterceptedMethod<Object>> intercepted, List<ExecutableMethod<Object, Object>> executableMethods) {
        BeanDefinition<Object> definition = (BeanDefinition<Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{BeanDefinition.class}, (self, method, args) -> switch (method.getName()) {
            case "getBeanType" -> Object.class;
            case "getExecutableMethods" -> executableMethods;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "definition";
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return (RuntimeProxyDefinition<Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{RuntimeProxyDefinition.class}, (self, method, args) -> switch (method.getName()) {
            case "interceptedMethods" -> intercepted;
            case "proxyBeanDefinition" -> definition;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "proxy definition";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }
}
