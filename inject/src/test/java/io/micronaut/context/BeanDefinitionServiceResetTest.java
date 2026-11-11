package io.micronaut.context;

import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A closed context can stay reachable, for example from a test extension. Its bean definition
 * service must then release the beans of the run it served: a lookup cache keeping the
 * definitions of that run kept every bean they supply.
 */
class BeanDefinitionServiceResetTest {

    @Test
    void closedContextReleasesTheBeansTheReplacingBeansCacheFound() {
        ApplicationContext context = ApplicationContext.builder().build();
        WeakReference<Replacement> bean = registerReplacement(context);
        context.start();
        try {
            assertNotNull(context.getBean(Replacement.class));
            // a lookup by qualifier alone caches the replacing beans
            assertFalse(context.getBeanDefinitions(Qualifiers.byName("replacement")).isEmpty());
        } finally {
            context.close();
        }

        assertReleased(bean);
        assertFalse(context.isRunning());
    }

    @Test
    void closedContextReleasesTheBeansTheDefinitionClassIndexFound() {
        ApplicationContext context = ApplicationContext.builder().build();
        WeakReference<Replacement> bean = registerReplacement(context);
        context.start();
        try {
            assertNotNull(context.getBean(Replacement.class));
            // a lookup by definition class indexes the definitions by their class name
            assertFalse(context.findBeanDefinitionByDefinitionClass(NoSuchDefinition.class).isPresent());
        } finally {
            context.close();
        }

        assertReleased(bean);
        assertFalse(context.isRunning());
    }

    private static WeakReference<Replacement> registerReplacement(ApplicationContext context) {
        Replacement replacement = new Replacement();
        context.registerBeanDefinition(
            RuntimeBeanDefinition.builder(replacement)
                .named("replacement")
                .replaces(Replaced.class)
                .build()
        );
        return new WeakReference<>(replacement);
    }

    private static void assertReleased(WeakReference<?> reference) {
        List<byte[]> pressure = new ArrayList<>();
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            if (reference.get() != null) {
                pressure.add(new byte[1024 * 1024]);
                if (pressure.size() > 32) {
                    pressure.clear();
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        assertNull(reference.get(), "the closed context still holds a bean of the run it served");
    }

    static class Replacement {
    }

    static class Replaced extends Replacement {
    }

    @SuppressWarnings("unused")
    abstract static class NoSuchDefinition implements io.micronaut.inject.BeanDefinition<Object> {
    }
}
