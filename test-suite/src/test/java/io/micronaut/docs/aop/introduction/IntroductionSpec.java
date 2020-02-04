package io.micronaut.docs.aop.introduction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

public class IntroductionSpec {

    @Test
    public void testStubIntroduction() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            // tag::test[]
            StubExample stubExample = applicationContext.getBean(StubExample.class);

            assertEquals(10, stubExample.getNumber());
            assertNull(stubExample.getDate());
            // end::test[]
        }
    }
}
