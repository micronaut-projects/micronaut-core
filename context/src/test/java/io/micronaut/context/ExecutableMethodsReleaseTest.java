package io.micronaut.context;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.context.env.Environment;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The executable methods a generated bean definition holds statically, in {@code $EXEC}, are shared by every context
 * that loads the definition class, and a development launcher loads it in its parent tier, once for every generation.
 * Each loaded definition configures a copy of them, so neither a development context nor its environment is reachable
 * from the shared methods, while the context runs or once it stopped.
 */
class ExecutableMethodsReleaseTest {

    @Test
    void aDevelopmentContextIsNotReachableFromTheSharedExecutableMethods() throws Exception {
        ApplicationContext context = run("/first");
        Environment environment = context.getEnvironment();
        BeanDefinition<ReleasedBean> definition = context.getBeanDefinition(ReleasedBean.class);
        assertEquals("/first", path(definition));
        Object shared = sharedMethods(definition);
        assertFalse(reachable(shared, environment), "the environment of the running context is reachable");
        assertFalse(reachable(shared, context), "the running context is reachable");

        context.stop();

        assertFalse(reachable(shared, environment), "the environment of the stopped context is still reachable");
        assertFalse(reachable(shared, context), "the stopped context is still reachable");
    }

    @Test
    void aDefinitionLoadedThenDisabledByItsOwnConditionsKeepsNoContext() throws Exception {
        ApplicationContext context = run("/disabled");
        Environment environment = context.getEnvironment();
        assertFalse(context.findBeanDefinition(DisabledReleasedBean.class).isPresent());
        Object shared = sharedMethods(Class.forName(ExecutableMethodsReleaseTest.class.getPackageName() + ".$ExecutableMethodsReleaseTest$DisabledReleasedBean$Definition"));
        assertFalse(reachable(shared, environment), "the environment of the running context is reachable");

        context.stop();

        assertFalse(reachable(shared, environment), "the environment of the stopped context is still reachable");
    }

    @Test
    void contextsRunningSideBySideEachKeepTheirOwnConfiguration() throws Exception {
        ApplicationContext first = run("/first");
        ApplicationContext second = run("/second");
        try {
            BeanDefinition<ReleasedBean> firstDefinition = first.getBeanDefinition(ReleasedBean.class);
            BeanDefinition<ReleasedBean> secondDefinition = second.getBeanDefinition(ReleasedBean.class);
            assertEquals(firstDefinition.getClass(), secondDefinition.getClass());
            assertEquals("/first", path(firstDefinition));
            assertEquals("/second", path(secondDefinition));
            Object shared = sharedMethods(secondDefinition);

            first.stop();

            assertEquals("/second", path(secondDefinition));
            assertFalse(reachable(shared, first.getEnvironment()));
            assertFalse(reachable(shared, second.getEnvironment()));
        } finally {
            first.close();
            second.close();
        }
    }

    private static ApplicationContext run(String path) {
        return ApplicationContext.run(Map.of(
            "spec.name", "ExecutableMethodsReleaseTest",
            "released.path", path,
            DevelopmentMode.PROPERTY, true));
    }

    private static String path(BeanDefinition<ReleasedBean> definition) {
        ExecutableMethod<ReleasedBean, Object> method = definition.findMethod("run").orElseThrow();
        return method.stringValue(Named.class).orElse(null);
    }

    /**
     * @return The executable methods the definition class holds statically, for every context
     */
    private static Object sharedMethods(BeanDefinition<?> definition) throws ReflectiveOperationException {
        return sharedMethods(definition.getClass());
    }

    private static Object sharedMethods(Class<?> definitionClass) throws ReflectiveOperationException {
        Field field = definitionClass.getDeclaredField("$EXEC");
        field.setAccessible(true);
        assertTrue(Modifier.isStatic(field.getModifiers()));
        return field.get(null);
    }

    /**
     * Whether the target is reachable from the root through the instance fields of the objects of unnamed modules, the
     * elements of arrays and the contents of collections and maps. The other objects of the JDK are not looked into.
     */
    private static boolean reachable(Object root, Object target) throws IllegalAccessException {
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Object> next = new ArrayDeque<>();
        next.push(root);
        while (!next.isEmpty()) {
            Object current = next.pop();
            if (current == target) {
                return true;
            }
            if (!seen.add(current) || current instanceof Class<?> || current instanceof ClassLoader || current instanceof Thread) {
                continue;
            }
            Class<?> type = current.getClass();
            if (type.isArray()) {
                if (!type.getComponentType().isPrimitive()) {
                    for (int i = 0; i < Array.getLength(current); i++) {
                        push(next, Array.get(current, i));
                    }
                }
            } else if (current instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    push(next, entry.getKey());
                    push(next, entry.getValue());
                }
            } else if (current instanceof Collection<?> collection) {
                for (Object element : collection) {
                    push(next, element);
                }
            } else if (!type.getModule().isNamed()) {
                for (Class<?> c = type; c != null && !c.getModule().isNamed(); c = c.getSuperclass()) {
                    for (Field field : c.getDeclaredFields()) {
                        if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                            field.setAccessible(true);
                            push(next, field.get(current));
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void push(Deque<Object> next, Object value) {
        if (value != null) {
            next.push(value);
        }
    }

    @Requires(property = "spec.name", value = "ExecutableMethodsReleaseTest")
    @Singleton
    static class ReleasedBean {
        @Executable
        @Named("${released.path}")
        public String run() {
            return "run";
        }
    }

    @Requires(property = "spec.name", value = "ExecutableMethodsReleaseTest")
    @Requires(beans = ExecutableMethodsReleaseTest.class)
    @Singleton
    static class DisabledReleasedBean {
        @Executable
        @Named("${released.path}")
        public String run() {
            return "run";
        }
    }
}
