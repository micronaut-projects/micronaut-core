package io.micronaut.docs.expressions;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
public class AnnotationContextExampleTest {
    @Inject BeanContext beanContext;
    @Test
    void testAnnotationContextEvaluation() {
        BeanDefinition<Example> beanDefinition = beanContext.getBeanDefinition(Example.class);
        String val = beanDefinition.stringValue(CustomAnnotation.class).orElse(null);
        Assertions.assertEquals(val, "first valuesecond value");
    }
}
