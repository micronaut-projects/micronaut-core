package io.micronaut.context;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.exceptions.NonUniqueBeanException;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import spock.lang.Specification;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBeanContextTest extends Specification {
    @Test
    void testMultipleSecondaryBeans() {
        try (DefaultBeanContext beanContext = new DefaultBeanContext()) {
            beanContext.configure();
            NonUniqueBeanException e = Assertions.assertThrows(
                NonUniqueBeanException.class,
                () -> beanContext.getBean(Foo.class)
            );
            Assertions.assertTrue(
                "Multiple possible bean candidates found: [Foo1, Foo2]".equals(e.getMessage()) ||
                    "Multiple possible bean candidates found: [Foo2, Foo1]".equals(e.getMessage()),
                "Exception message was incorrect. Expected \"Multiple possible bean candidates found: [Foo1, Foo2]\"; got " + e.getMessage()
            );
        }
    }
    // classes used by testMultipleSecondaryBeans
    interface Foo {}
    @Singleton
    @Secondary
    static class Foo1 implements Foo {}
    @Singleton
    @Secondary
    static class Foo2 implements Foo {}
    static class BeanFromDefinition {}
    interface AdoptedService {}
    record FirstAdoptedService() implements AdoptedService {}
    record SecondAdoptedService() implements AdoptedService {}

    @Test
    void beanKeyEqualityIncludesQualifierAndTypeArguments() {
        Qualifier<BeanFromDefinition> definitionQualifier = Qualifiers.byName("definitionBean");
        RuntimeBeanDefinition<BeanFromDefinition> definition = RuntimeBeanDefinition.of(BeanFromDefinition.class, BeanFromDefinition::new);

        DefaultBeanContext.BeanKey<BeanFromDefinition> fromDefinition = new DefaultBeanContext.BeanKey<>(definition, definitionQualifier);
        assertEquals(new DefaultBeanContext.BeanKey<>(definition.asArgument(), definitionQualifier), fromDefinition);
        assertEquals(definitionQualifier + " " + definition.asArgument().getName(), fromDefinition.toString());

        Argument<CharSequence> beanType = Argument.of(CharSequence.class);
        DefaultBeanContext.BeanKey<CharSequence> unqualified = new DefaultBeanContext.BeanKey<>(beanType, null);
        DefaultBeanContext.BeanKey<CharSequence> sameUnqualified = new DefaultBeanContext.BeanKey<>(Argument.of(CharSequence.class), null);

        assertTrue(unqualified.equals(unqualified));
        assertFalse(unqualified.equals(null));
        assertEquals(unqualified, sameUnqualified);
        assertEquals(unqualified.hashCode(), sameUnqualified.hashCode());
        assertNotEquals(unqualified, new DefaultBeanContext.BeanKey<>(beanType, Qualifiers.byName("primaryString")));

        Qualifier<List> listQualifier = Qualifiers.byName("strings");
        Argument<List> stringList = Argument.of(List.class, String.class);
        DefaultBeanContext.BeanKey<List> fromClass = new DefaultBeanContext.BeanKey<>(List.class, listQualifier, String.class);

        assertEquals(new DefaultBeanContext.BeanKey<>(stringList, listQualifier), fromClass);
        assertEquals(listQualifier + " " + stringList.getName(), fromClass.toString());
        assertNotEquals(fromClass, new DefaultBeanContext.BeanKey<>(List.class, listQualifier, Integer.class));
        assertNotEquals(
            new DefaultBeanContext.BeanKey<>(Argument.listOf(String.class), Qualifiers.byName("strings")),
            new DefaultBeanContext.BeanKey<>(Argument.listOf(String.class), Qualifiers.byName("other"))
        );
    }

    @Test
    void runtimeSingletonRegistrationInvalidatesLookupCachesAndResolvesNamedCollections() {
        try (DefaultBeanContext beanContext = minimalBeanContext()) {
            beanContext.start();

            Qualifier<AdoptedService> firstQualifier = Qualifiers.byName("first");
            Qualifier<AdoptedService> secondQualifier = Qualifiers.byName("second");
            AdoptedService first = new FirstAdoptedService();
            AdoptedService second = new SecondAdoptedService();

            assertFalse(beanContext.containsBean(AdoptedService.class, firstQualifier));

            beanContext.registerSingleton(AdoptedService.class, first, firstQualifier, false);
            beanContext.registerSingleton(AdoptedService.class, second, secondQualifier, false);

            assertTrue(beanContext.containsBean(AdoptedService.class, firstQualifier));
            assertSame(first, beanContext.getBean(AdoptedService.class, firstQualifier));
            assertSame(second, beanContext.findBean(AdoptedService.class, secondQualifier).orElseThrow());

            var services = beanContext.getBeansOfType(AdoptedService.class);
            assertEquals(Set.of(first, second), Set.copyOf(services));
            assertEquals(List.of(first), beanContext.streamOfType(AdoptedService.class, firstQualifier).toList());

            var servicesByName = beanContext.mapOfType(Argument.of(AdoptedService.class), null);
            assertEquals(Set.of("first", "second"), servicesByName.keySet());
            assertSame(first, servicesByName.get("first"));
            assertSame(second, servicesByName.get("second"));

            assertSame(
                beanContext.getBeanRegistrations(AdoptedService.class),
                beanContext.getBeanRegistrations(AdoptedService.class)
            );
        }
    }

    private static DefaultBeanContext minimalBeanContext() {
        return new DefaultBeanContext(new BeanContextConfiguration() {
            @Override
            public boolean eagerBeansEnabled() {
                return false;
            }

            @Override
            public boolean eventsEnabled() {
                return false;
            }
        });
    }
}
