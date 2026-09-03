package io.micronaut.docs.inject.retainable;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.BeanDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RetainableSpec {

    @Test
    void testEachComposedAnnotationReportsTheOccurrenceItIntroduced() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<CodeValidator> definition = context.getBeanDefinition(CodeValidator.class);

            //tag::read[]
            AnnotationValue<?> min = definition.getAnnotation(MinLength.class).getStereotypes().get(0);
            AnnotationValue<?> max = definition.getAnnotation(MaxLength.class).getStereotypes().get(0);

            assertEquals(Limit.class.getName(), min.getAnnotationName());
            assertEquals(Map.of("min", 3), min.getValues()); // @Limit(min = 3)
            assertEquals(Map.of("max", 9), max.getValues()); // @Limit(max = 9)
            //end::read[]
        }
    }

    @Test
    void testTheFlatIndexCannotAttributeTheOccurrences() {
        try (ApplicationContext context = ApplicationContext.run()) {
            BeanDefinition<CodeValidator> definition = context.getBeanDefinition(CodeValidator.class);

            assertEquals(
                List.of(MinLength.class.getName(), MaxLength.class.getName()),
                List.copyOf(definition.getAnnotationNamesByStereotype(Limit.class))
            );
        }
    }
}
