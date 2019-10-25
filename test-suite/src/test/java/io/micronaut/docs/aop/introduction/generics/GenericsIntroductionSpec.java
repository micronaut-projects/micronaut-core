package io.micronaut.docs.aop.introduction.generics;

import static org.junit.Assert.assertEquals;

import io.micronaut.context.ApplicationContext;
import org.junit.Test;

public class GenericsIntroductionSpec {

    @Test
    public void testStubIntroduction() {
        ApplicationContext applicationContext = ApplicationContext.run();

        // tag::test[]
        SpecificPublisher publisher = applicationContext.getBean(SpecificPublisher.class);

        assertEquals("SpecificEvent", publisher.publish(new SpecificEvent()));
        // end::test[]

        applicationContext.stop();
    }
    
}
