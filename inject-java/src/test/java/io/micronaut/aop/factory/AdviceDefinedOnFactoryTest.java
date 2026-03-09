package io.micronaut.aop.factory;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import io.micronaut.inject.writer.BeanDefinitionVisitor;
import io.micronaut.inject.writer.BeanDefinitionWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Java port of AdviceDefinedOnFactorySpec.
 */
class AdviceDefinedOnFactoryTest extends AbstractTypeElementTest {

    @Test
    void testAdviceDefinedAtFactoryClassLevel() {
        String className = "test.$MyFactory" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionVisitor.PROXY_SUFFIX;

        String source = """
            package test;

            import io.micronaut.context.annotation.*;
            import io.micronaut.aop.simple.Mutating;

            @Factory
            @Mutating("name")
            class MyFactory {

                @Bean
                @Executable
                String myBean(@Parameter String name) {
                    return name;
                }
            }
            """;

        BeanDefinition<?> beanDefinition = buildBeanDefinition(className, source);
        assertNotNull(beanDefinition, "Expected generated bean definition for factory proxy");
        assertEquals(1, beanDefinition.getExecutableMethods().size(), "Factory should expose 1 executable method");
    }
}
