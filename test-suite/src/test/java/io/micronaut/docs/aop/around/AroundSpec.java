package io.micronaut.docs.aop.around;

import io.micronaut.context.ApplicationContext;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AroundSpec {

    // tag::test[]
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testNotNull() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            NotNullExample exampleBean = applicationContext.getBean(NotNullExample.class);

            thrown.expect(IllegalArgumentException.class);
            thrown.expectMessage("Null parameter [taskName] not allowed");

            exampleBean.doWork(null);
        }
    }
    // end::test[]
}
