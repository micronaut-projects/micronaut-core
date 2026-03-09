package io.micronaut.aop.introduction;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.core.reflect.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Java port of InterceptorQualifierSpec.
 */
class InterceptorQualifierTest {

    /**
     * Try to retrieve a property via getter (getX/isX/x) then via a field.
     */
    private static Object prop(Object instance, String name) {
        try {
            Class<?> cls = instance.getClass();
            String capital = name.substring(0, 1).toUpperCase() + name.substring(1);
            for (String mn : new String[]{"get" + capital, "is" + capital, name}) {
                try {
                    Method m = cls.getMethod(mn);
                    m.setAccessible(true);
                    return m.invoke(instance);
                } catch (NoSuchMethodException ignored) {
                }
            }
            try {
                // Prefer ReflectionUtils for field access (public or declared)
                return ReflectionUtils.getField(cls, name, instance);
            } catch (Throwable nf) {
                // Fallback to declared field if ReflectionUtils path fails for any reason
                try {
                    Field f = cls.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(instance);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to access property '" + name + "' on " + instance.getClass(), e);
        }
    }


    @Test
    void testInterceptedQualifier() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of("spec.name", "InterceptorQualifierSpec"))) {

            // MyDataSourceHelper with name "FOO"
            MyDataSourceHelper fooHelper = applicationContext.getBean(MyDataSourceHelper.class, Qualifiers.byName("FOO"));
            assertEquals("FOO", prop(fooHelper, "name"));
            assertEquals("FOO", prop(fooHelper, "injectionPointQualifier"));

            Object helper2 = prop(fooHelper, "helper2");
            assertNotNull(helper2);
            assertNull(prop(helper2, "name"));
            assertEquals("FOO", prop(helper2, "injectionPointQualifier"));

            Object helper3 = prop(fooHelper, "helper3");
            assertNotNull(helper3);
            assertEquals("FOO", prop(helper3, "name"));
            assertEquals("FOO", prop(helper3, "injectionPointQualifier"));

            // MyDataSourceHelper with name "BAR"
            MyDataSourceHelper barHelper = applicationContext.getBean(MyDataSourceHelper.class, Qualifiers.byName("BAR"));
            assertEquals("BAR", prop(barHelper, "name"));
            Object barHelper2 = prop(barHelper, "helper2");
            assertNotNull(barHelper2);
            assertNull(prop(barHelper2, "name"));

            // MyInterceptedInterface qualifiers
            MyInterceptedInterface fooInterceptor = applicationContext.getBean(MyInterceptedInterface.class, Qualifiers.byName("FOO"));
            assertEquals("FOO", prop(fooInterceptor, "value"));

            MyInterceptedInterface barInterceptor = applicationContext.getBean(MyInterceptedInterface.class, Qualifiers.byName("BAR"));
            assertEquals("BAR", prop(barInterceptor, "value"));

            // Wrapper beans
            MyInterceptedInterfaceWrapper fooWrapper = applicationContext.getBean(MyInterceptedInterfaceWrapper.class, Qualifiers.byName("FOO"));
            Object fooInner = prop(fooWrapper, "myInterceptedInterface");
            assertNotNull(fooInner);
            assertEquals("FOO", prop(fooInner, "value"));

            MyInterceptedInterfaceWrapper barWrapper = applicationContext.getBean(MyInterceptedInterfaceWrapper.class, Qualifiers.byName("BAR"));
            Object barInner = prop(barWrapper, "myInterceptedInterface");
            assertNotNull(barInner);
            assertEquals("BAR", prop(barInner, "value"));
        }
    }
}
