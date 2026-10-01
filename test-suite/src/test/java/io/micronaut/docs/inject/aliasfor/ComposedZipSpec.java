package io.micronaut.docs.inject.aliasfor;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ComposedZipSpec {

    @Test
    void testTheAliasedMemberDefaultOverridesTheDeclaredStereotypeValue() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<ZipCodeValidator> definition = context.getBeanDefinition(ZipCodeValidator.class);
            assertEquals(5, definition.intValue(Size.class, "min").orElse(-1));
            assertEquals(10, definition.intValue(Size.class, "max").orElse(-1));
        }
    }

    @Test
    void testAnExplicitlySetMemberOverridesTheDeclaredStereotypeValue() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<CustomZipCodeValidator> definition = context.getBeanDefinition(CustomZipCodeValidator.class);
            assertEquals(5, definition.intValue(Size.class, "min").orElse(-1));
            assertEquals(20, definition.intValue(Size.class, "max").orElse(-1));
        }
    }
}
