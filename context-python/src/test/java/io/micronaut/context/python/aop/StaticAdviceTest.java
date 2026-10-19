package io.micronaut.context.python.aop;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.runtime.RuntimeProxyDefinition;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
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
        NullPointerException none = assertThrows(NullPointerException.class, () -> StaticAdvice.result(null, "int"));
        assertTrue(none.getMessage().contains("'int'"), none.getMessage());
    }

    @Test
    void anIntroductionRefusesACallItsInterceptorsDoNotAnswerInsteadOfCallingItself() {
        Object proxy = new Object();
        RuntimeProxyDefinition<Object> withoutEntry = proxyDefinition(List.of());
        IllegalStateException unanswered = assertThrows(IllegalStateException.class, () ->
            StaticAdvice.introduction(withoutEntry, List.of(), () -> proxy).proceed("count", new String[0], new Object[0]));
        assertTrue(unanswered.getMessage().contains("[count]") && unanswered.getMessage().contains("no introduction interceptor answers it"), unanswered.getMessage());

        @SuppressWarnings("unchecked")
        RuntimeProxyDefinition.InterceptedMethod<Object> concrete = new RuntimeProxyDefinition.InterceptedMethod<>(executableMethod("label", false), new Interceptor[0]);
        RuntimeProxyDefinition<Object> withConcrete = proxyDefinition(List.of(concrete));
        IllegalStateException notAbstract = assertThrows(IllegalStateException.class, () ->
            StaticAdvice.introduction(withConcrete, List.of(concrete), () -> proxy).proceed("label", new String[0], new Object[0]));
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
}
