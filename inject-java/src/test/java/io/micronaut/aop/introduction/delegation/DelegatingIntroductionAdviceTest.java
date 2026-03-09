package io.micronaut.aop.introduction.delegation;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java port of DelegatingIntroductionAdviceSpec.
 */
class DelegatingIntroductionAdviceTest {


    @Test
    void testThatDelegationAdviceWorks() {
        try (ApplicationContext context = ApplicationContext.run()) {
            DelegatingIntroduced delegating = (DelegatingIntroduced) context.getBean(Delegating.class);

            assertEquals("good", delegating.test2());
            assertEquals("good", delegating.test());
        }
    }
}
