package io.micronaut.aop.introduction;

import io.micronaut.aop.Intercepted;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java port of AbstractClassIntroductionAdviceSpec.
 */
class AbstractClassIntroductionAdviceTest {


    @Test
    void testAopMethodInvocationNamedBeanForMethods() {
        try (ApplicationContext context = ApplicationContext.run()) {
            AbstractClass foo = context.getBean(AbstractClass.class);

            assertTrue(foo instanceof Intercepted);

            // test for single string arg
            assertEquals("changed", foo.test("test"));
            // test for single string arg on non-abstract forwarding method
            assertEquals("changed", foo.nonAbstract("test"));
            // test for multiple args, one primitive
            assertEquals("changed", foo.test("test", 10));
            // test for multiple args, one primitive through generic super interface method
            assertEquals("changed", ((SuperInterface) foo).testGenericsFromType("test", 10));
        }
    }
}
