package io.micronaut.inject.visitor.beans;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.DefaultApplicationContext;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertNotNull;

public class ContextBeanTest {
    @Test
    public void testApplicationContextScalaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.scala.TestContextBean singletonBean = applicationContext.getBean(test.scala.TestContextBean.class);

        assertNotNull(singletonBean);
    }

    @Test
    public void testApplicationContextJavaBean() {
        ApplicationContext applicationContext = new DefaultApplicationContext();
        applicationContext.start();

        test.java.TestContextBean singletonBean = applicationContext.getBean(test.java.TestContextBean.class);

        assertNotNull(singletonBean);
    }
}
