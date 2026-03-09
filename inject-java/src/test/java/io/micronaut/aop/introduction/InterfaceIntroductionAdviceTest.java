package io.micronaut.aop.introduction;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.test.AbstractTypeElementTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of InterfaceIntroductionAdviceSpec.
 */
class InterfaceIntroductionAdviceTest extends AbstractTypeElementTest {

    @Test
    void testAopMethodInvocationNamedBeanForMethods() {
        try (ApplicationContext context = ApplicationContext.run()) {
            InterfaceIntroductionClass<?> foo = context.getBean(InterfaceIntroductionClass.class);

            assertTrue(foo instanceof Intercepted);

            // test for single string arg
            assertEquals("changed", foo.test("test"));
            // test for multiple args, one primitive
            assertEquals("changed", foo.test("test", 10));
            // test for multiple args, one primitive through generic super interface method (use raw super to avoid wildcard capture)
            assertEquals("changed", ((SuperInterface) foo).testGenericsFromType("test", 10));
        }
    }

    @Test
    void testInjectingAnIntroductionAdviceWithGenerics() {
        try (ApplicationContext context = ApplicationContext.run()) {
            // should not throw
            context.getBean(InjectParentInterface.class);
        }
    }

    @Test
    void testTypeArgumentsMapAreCreatedForIntroductionAdvice() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$Intercepted", """
            package test;

            import java.util.List;
            import io.micronaut.aop.introduction.ParentInterface;
            import io.micronaut.aop.introduction.Stub;

            @Stub
            interface Test extends ParentInterface<List<String>> {
            }
            """);

        assertFalse(definition.getTypeArguments(ParentInterface.class).isEmpty());
    }

    @Test
    void testTypeArgumentAnnotationPropagation() {
        BeanDefinition<?> definition = buildBeanDefinition("test.Test$Intercepted", """
            package test;

            import java.util.List;
            import io.micronaut.aop.introduction.DataCrudRepo;
            import io.micronaut.aop.introduction.Stub;
            import jakarta.validation.Valid;
            import jakarta.validation.constraints.Min;

            @Stub
            interface Test extends DataCrudRepo<@Valid String, @Min(5) Integer> {
            }
            """);

        assertTrue(definition.getRequiredMethod("save", String.class)
            .getArguments()[0]
            .getAnnotationMetadata()
            .hasAnnotation(Valid.class));

        assertTrue(definition.getRequiredMethod("findById", Integer.class)
            .getArguments()[0]
            .getAnnotationMetadata()
            .hasAnnotation(Min.class));

        assertTrue(definition.getRequiredMethod("findById", Integer.class)
            .getReturnType()
            .getAnnotationMetadata()
            .hasAnnotation(Valid.class));
    }
}
