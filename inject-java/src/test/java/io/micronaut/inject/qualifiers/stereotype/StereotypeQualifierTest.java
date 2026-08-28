package io.micronaut.inject.qualifiers.stereotype;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.Qualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StereotypeQualifierTest {

    @Test
    void testByStereotypeQualifier() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "StereotypeQualifierSpec"))) {
            Qualifier<Object> qualifier = Qualifiers.byQualifiers(Qualifiers.byStereotype(MyStereotype.class));
            var definitions = context.getBeanDefinitions(qualifier);
            assertEquals(1, definitions.size());
            var beanType = definitions.iterator().next().getBeanType();
            assertEquals(MyBean.class, beanType);
        }
    }

    @Test
    void testByNamedStereotypeQualifier() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "StereotypeQualifierSpec"))) {
            Qualifier<Object> qualifier = Qualifiers.byQualifiers(Qualifiers.byStereotype(MyStereotype.class.getName()));
            var definitions = context.getBeanDefinitions(qualifier);
            assertEquals(1, definitions.size());
            var beanType = definitions.iterator().next().getBeanType();
            assertEquals(MyBean.class, beanType);
        }
    }

    @Test
    void testByRepeatableStereotypeQualifier() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "StereotypeQualifierSpec"))) {
            Qualifier<Object> qualifier = Qualifiers.byQualifiers(Qualifiers.byStereotype(MyRepeatableStereotype.class));
            var definitions = context.getBeanDefinitions(qualifier);
            assertEquals(1, definitions.size());
            var beanType = definitions.iterator().next().getBeanType();
            assertEquals(MyBean2.class, beanType);
        }
    }

    @Test
    void testByRepeatableNamedStereotypeQualifier() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "StereotypeQualifierSpec"))) {
            Qualifier<Object> qualifier = Qualifiers.byQualifiers(Qualifiers.byStereotype(MyRepeatableStereotype.class.getName()));
            var definitions = context.getBeanDefinitions(qualifier);
            assertEquals(1, definitions.size());
            var beanType = definitions.iterator().next().getBeanType();
            assertEquals(MyBean2.class, beanType);
        }
    }
}
