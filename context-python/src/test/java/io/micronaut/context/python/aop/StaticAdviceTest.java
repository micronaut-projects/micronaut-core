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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticAdviceTest {

    @Test
    void anInterceptorResultIsConvertedToTheReturnTypeWhenItFitsExactly() {
        assertEquals(4, StaticAdvice.result(4L, "int"));
        assertEquals(4L, StaticAdvice.result(4, "long"));
        assertEquals(4, StaticAdvice.result(4L, "java.lang.Integer"));
        assertEquals(1.5, StaticAdvice.result(1.5, "double"));
        assertEquals(4.0, StaticAdvice.result(4L, "double"));
        assertEquals(4.0, StaticAdvice.result(4, "java.lang.Double"));
        assertEquals(true, StaticAdvice.result(true, "boolean"));
        assertEquals("x", StaticAdvice.result("x", "java.lang.String"));
        assertEquals('x', StaticAdvice.result("x", "char"));
        assertEquals('x', StaticAdvice.result('x', "char"));
        assertEquals('x', StaticAdvice.result("x", "java.lang.Character"));
        assertNull(StaticAdvice.result(null, "java.lang.Integer"));
        assertNull(StaticAdvice.result(null, "java.lang.Object"));
    }

    @Test
    void anInterceptorResultThatDoesNotFitFailsAsTheBridgeConversionDoes() {
        assertThrows(ClassCastException.class, () -> StaticAdvice.result(2.5, "int"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result(1L << 40, "int"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result((1L << 53) + 1, "double"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result("4", "int"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result(1L, "boolean"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result("xy", "char"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result("", "char"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result(120L, "char"));
        assertThrows(ClassCastException.class, () -> StaticAdvice.result("xy", "java.lang.Character"));
        assertThrows(NullPointerException.class, () -> StaticAdvice.result(null, "char"));
        NullPointerException none = assertThrows(NullPointerException.class, () -> StaticAdvice.result(null, "int"));
        assertTrue(none.getMessage().contains("'int'"), none.getMessage());
    }

    @Test
    void anIntroductionRefusesACallItsInterceptorsDoNotAnswerInsteadOfCallingItself() {
        Object proxy = new Object();
        RuntimeProxyDefinition<Object> withoutEntry = proxyDefinition(List.of());
        var unansweredAdvice = StaticAdvice.introduction(withoutEntry, List.of(), () -> proxy);
        IllegalStateException unanswered = assertThrows(IllegalStateException.class, () ->
            unansweredAdvice.proceed("count", new String[0], new Object[0]));
        assertTrue(unanswered.getMessage().contains("[count]") && unanswered.getMessage().contains("no introduction interceptor answers it"), unanswered.getMessage());

        @SuppressWarnings("unchecked")
        RuntimeProxyDefinition.InterceptedMethod<Object> concrete = new RuntimeProxyDefinition.InterceptedMethod<>(executableMethod("label", false), new Interceptor[0]);
        RuntimeProxyDefinition<Object> withConcrete = proxyDefinition(List.of(concrete));
        var concreteAdvice = StaticAdvice.introduction(withConcrete, List.of(concrete), () -> proxy);
        IllegalStateException notAbstract = assertThrows(IllegalStateException.class, () ->
            concreteAdvice.proceed("label", new String[0], new Object[0]));
        assertTrue(notAbstract.getMessage().contains("[label]") && notAbstract.getMessage().contains("not abstract"), notAbstract.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAdaptedMethodRunsTheAdapterIntroductionAloneAsThePythonProxyDoes() {
        List<String> ran = new java.util.ArrayList<>();
        Interceptor<Object, Object> around = context -> {
            ran.add("around");
            return context.proceed();
        };
        Interceptor<Object, Object> adapter = context -> {
            ran.add("adapter");
            return "adapted";
        };
        RuntimeProxyDefinition.InterceptedMethod<Object> adapted = new RuntimeProxyDefinition.InterceptedMethod<>(
            executableMethod("onApplicationEvent", true, true), new Interceptor[]{around, adapter});
        Object proxy = new Object();
        Object result = StaticAdvice.introduction(proxyDefinition(List.of(adapted)), List.of(adapted), () -> proxy)
            .proceed("onApplicationEvent", new String[0], new Object[0]);
        assertEquals("adapted", result);
        // the around interceptors of an adapted method advise the target method, not the adapter
        assertEquals(List.of("adapter"), ran);
    }

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

    private static ExecutableMethod<Object, Object> executableMethod(String name, boolean isAbstract) {
        return executableMethod(name, isAbstract, false);
    }

    @SuppressWarnings("unchecked")
    private static ExecutableMethod<Object, Object> executableMethod(String name, boolean isAbstract, boolean adapter) {
        return (ExecutableMethod<Object, Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{ExecutableMethod.class}, (self, method, args) -> switch (method.getName()) {
            case "getMethodName" -> name;
            case "getArguments" -> Argument.ZERO_ARGUMENTS;
            case "isAbstract" -> isAbstract;
            case "hasStereotype" -> adapter && args[0] == io.micronaut.aop.Adapter.class;
            case "getAnnotationMetadata" -> io.micronaut.core.annotation.AnnotationMetadata.EMPTY_METADATA;
            case "getDeclaringType" -> Object.class;
            case "getReturnType" -> io.micronaut.core.type.ReturnType.of(Object.class);
            case "invoke" -> throw new AssertionError("the method of an introduction must not be invoked on the proxy");
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> name;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static RuntimeProxyDefinition<Object> proxyDefinition(List<RuntimeProxyDefinition.InterceptedMethod<Object>> intercepted) {
        BeanDefinition<Object> definition = (BeanDefinition<Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{BeanDefinition.class}, (self, method, args) -> switch (method.getName()) {
            case "getBeanType" -> Object.class;
            case "getExecutableMethods" -> List.of();
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "definition";
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return (RuntimeProxyDefinition<Object>) Proxy.newProxyInstance(StaticAdviceTest.class.getClassLoader(), new Class<?>[]{RuntimeProxyDefinition.class}, (self, method, args) -> switch (method.getName()) {
            case "interceptedMethods" -> intercepted;
            case "proxyBeanDefinition" -> definition;
            case "introduction" -> true;
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> self == args[0];
            case "toString" -> "proxy definition";
            default -> throw new UnsupportedOperationException(method.getName());
        });
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
